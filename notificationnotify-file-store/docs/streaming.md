# Streaming blob content — never buffer to a byte array

Production code must **never** download a blob into a `ByteArrayOutputStream` or
`byte[]`.  Large notification attachments and report files can be hundreds of
megabytes.  Buffering the full content in JVM heap is a memory hazard under load.
Always stream.

---

## Why `ByteArrayOutputStream` is forbidden

```java
// FORBIDDEN — entire file in heap
final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
blobClient.downloadStreamWithResponse(buffer, new BlobRange(0, 1_000_000_000L), ...);
final byte[] bytes = buffer.toByteArray();
```

This allocates a byte array the size of the blob plus the intermediate buffer inside
`ByteArrayOutputStream`.  For a 50 MB attachment that is ~100 MB of heap per concurrent
request.

---

## Why `openInputStream` and `downloadContent()` also fail

Neither is a safe alternative.  Both NPE on Azurite (and the same NPE can surface
on real Azure in edge cases where `Content-Range` is absent):

| Method | Failure mode |
|---|---|
| `blobClient.openInputStream()` | NPE in `BlobClientBase` — tries to parse `Content-Range` which is absent on full-file responses from Azurite |
| `blobClient.openInputStream(BlobInputStreamOptions)` | Same NPE |
| `blobClient.downloadContent()` | NPE — calls `getBlobLength()` which reads `Content-Length`, absent from full GET responses in WildFly's JDK HTTP transport |

The only download method that works reliably inside WildFly against Azurite **and**
real Azure is `downloadStreamWithResponse` with an explicit oversized `BlobRange`.

---

## Pattern 1 — streaming directly to an HTTP response

Use JAX-RS `StreamingOutput` when the caller is a resource method returning a
`Response`.  Pass the JAX-RS `OutputStream` directly to `downloadStreamWithResponse` —
bytes flow from Azure to the HTTP client with no intermediate buffer.

```java
import javax.ws.rs.core.StreamingOutput;
import com.azure.storage.blob.models.BlobRange;

final BlobClient blobClient =
        blobContainerClient.getBlobClient(BLOB_PATH.blobName(fileId));

if (!blobClient.exists()) {
    throw new RuntimeException("No file found for fileId=" + fileId);
}

// Oversized range forces 206 Partial Content → Content-Range header present
// → Azure SDK determines blob length without Content-Length (absent in WildFly JDK transport).
final StreamingOutput streamingOutput = output ->
        blobClient.downloadStreamWithResponse(output, new BlobRange(0, 1_000_000_000L),
                null, null, false, null, null);

return status(OK)
        .entity(streamingOutput)
        .header(CONTENT_TYPE, PDF_CONTENT_TYPE)
        .header(CONTENT_DISPOSITION, "attachment;filename=" + fileName)
        .build();
```

---

## Pattern 2 — streaming for processing (PipedInputStream + ManagedExecutorService)

Use when code needs to **read** the stream rather than write it to an output (for
example, counting PDF pages, computing a checksum, or extracting metadata).

`downloadStreamWithResponse` requires an `OutputStream` to write to.  A
`PipedInputStream`/`PipedOutputStream` pair bridges this: the download writes to
`PipedOutputStream` on a background thread; the processing code reads from the paired
`PipedInputStream` on the calling thread.  Bytes flow through a fixed-size pipe
buffer (64 KB) and are never accumulated in full.

The background thread **must** use `ManagedExecutorService` — plain `Thread.start()`
is not supported inside a managed WildFly container.

```java
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedExecutorService;
import com.azure.storage.blob.models.BlobRange;

@Resource
private ManagedExecutorService managedExecutorService;

private Optional<Integer> getPageCount(final UUID fileId) {
    final BlobClient blobClient =
            blobContainerClient.getBlobClient(BLOB_PATH.blobName(fileId));

    if (!blobClient.exists()) {
        return Optional.empty();
    }

    final PipedOutputStream pipedOutputStream = new PipedOutputStream();
    final PipedInputStream pipedInputStream;
    try {
        pipedInputStream = new PipedInputStream(pipedOutputStream, 65536);
    } catch (final IOException e) {
        logger.error("Failed to initialise download stream for fileId={}", fileId, e);
        return Optional.empty();
    }

    managedExecutorService.submit(() -> {
        try {
            blobClient.downloadStreamWithResponse(
                    pipedOutputStream, new BlobRange(0, 1_000_000_000L),
                    null, null, false, null, null);
        } finally {
            try {
                pipedOutputStream.close();
            } catch (final IOException e) {
                logger.warn("Failed to close download stream for fileId={}", fileId, e);
            }
        }
    });

    try (final PipedInputStream pdfStream = pipedInputStream) {
        return Optional.of(pdfHelper.getDocumentPageCount(pdfStream));
    } catch (final IOException e) {
        logger.error("Failed to read document content for fileId={}", fileId, e);
        return Optional.empty();
    }
}
```

Key points:
- The pipe buffer is 64 KB.  Download and processing threads run concurrently —
  the download blocks when the buffer is full; processing unblocks it as it consumes.
- Always close `PipedOutputStream` in a `finally` block.  If the download throws, the
  close signals EOF to the reader — without it the processing thread blocks forever.
- `ManagedExecutorService` is injected via `@Resource` (not `@Inject`).

---

## Getting file size

`blobClient.getProperties().getBlobSize()` returns a `long` and works correctly inside
WildFly.  It is a lightweight HEAD request.

```java
final long fileSize = blobClient.getProperties().getBlobSize();
```

Do **not** derive file size from `ByteArrayOutputStream.size()` — that returns
`int` and silently truncates for files larger than 2 GB.

---

## UC2 — peer-to-peer transfer (v6: SAS + `copyFromUrlWithResponse`)

For UC2, the receiving service does **not** stream bytes through its pod.  The owner
mints a short-lived read User Delegation SAS on the source blob and sends the URL.
The receiver calls `copyFromUrlWithResponse` — Azure Storage performs a server-side
copy and BYOFS-1.3 metadata is set atomically in the same operation.

```java
final BlobCopyFromUrlOptions copyOptions =
        new BlobCopyFromUrlOptions(sourceUri.toString())
                .setMetadata(Map.of(
                        "correlation_id", correlationId.toString(),
                        "filename", filename));

blobContainerClient
        .getBlobClient(BLOB_PATH.blobName(fileId))
        .copyFromUrlWithResponse(copyOptions, null, Context.NONE);
```

Key points:
- `copyFromUrlWithResponse` is synchronous — it completes before returning.
  Use for blobs where completion is required before the command handler returns.
- Metadata is set atomically on the destination via `BlobCopyFromUrlOptions.setMetadata()`.
  Do **not** call `setMetadata()` in a separate step after the copy.
- The source URL must be accessible by Azure Storage — use a read SAS with sufficient
  expiry (at minimum the expected copy duration plus clock skew).
- `FileIngestor` in `notificationnotify-file-store-core` encapsulates this pattern.

**v7 note:** The BYOFS-2 epic (not yet started) replaces the SAS with RBAC-only
cross-container access using `blobClient.beginCopy()` — no SAS required.  The
v7 approach is described in [`design-decisions.md`](design-decisions.md).

---

## Summary

| Scenario | Pattern |
|---|---|
| Serve blob as HTTP response | `StreamingOutput` → `downloadStreamWithResponse` (Pattern 1) |
| Process blob content — page count, metadata extraction | `PipedInputStream`/`PipedOutputStream` + `ManagedExecutorService` (Pattern 2) |
| Peer-to-peer copy (UC2, v6) | `copyFromUrlWithResponse` + `BlobCopyFromUrlOptions` — server-side, no streaming through pod |
| Get file size | `blobClient.getProperties().getBlobSize()` |
| Download to byte array | **FORBIDDEN** |
| `openInputStream()` / `downloadContent()` | **Do not use — NPE on Azurite** |
