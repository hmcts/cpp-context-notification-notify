# BYO FileStore — Reference example: direct Azure Blob SDK usage

Standalone Java class showing the four core BYO FileStore patterns — **no CDI, no JNDI, no WildFly**.
Copy this into your service and swap in your own connection string / endpoint / container name.

For the WildFly CDI wiring see `notificationnotify-file-store-core` — this snippet targets services on
other runtimes or anyone who wants to understand the raw SDK calls before wiring up CDI.

---

## Maven dependencies

```xml
<!-- Azure Blob SDK -->
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-storage-blob</artifactId>
    <version>12.25.1</version>
</dependency>
<!-- Workload Identity / DefaultAzureCredential -->
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-identity</artifactId>
    <version>1.11.4</version>
</dependency>
<!--
  JDK HTTP transport — avoids Netty classpath conflict with WildFly 26's bundled Netty modules.
  Drop this if your runtime does not bundle Netty, but it is safe to include everywhere.
-->
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-core-http-jdk-httpclient</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## Reference class

```java
import com.azure.core.http.jdk.httpclient.JdkHttpClientProvider;
import com.azure.core.util.BinaryData;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobCopyFromUrlOptions;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.UserDelegationKey;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static com.azure.core.util.BinaryData.fromBytes;
import static java.util.UUID.randomUUID;

/**
 * Standalone BYO FileStore reference — BYOFS-1.5.
 * Covers: client construction, UC1 upload/download, UC2 server-side copy, UC2 SAS minting.
 */
public final class BlobStoreExample {

    private static final long MAX_BLOB_BYTES = 1_000_000_000L;

    private BlobStoreExample() {}

    /** Thrown when a requested blob does not exist. */
    public static final class BlobNotFoundException extends RuntimeException {
        public BlobNotFoundException(final String blobName) {
            super("No blob found at path: " + blobName);
        }
    }

    // -----------------------------------------------------------------------
    // Client construction — choose one auth mode
    // -----------------------------------------------------------------------

    /** Connection-string auth: Azurite in dev/CI, or a storage account key in test. */
    public static BlobContainerClient forConnectionString(
            final String connectionString, final String containerName) {
        final BlobContainerClient containerClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .httpClientProvider(new JdkHttpClientProvider())
                .buildClient()
                .getBlobContainerClient(containerName);
        try {
            containerClient.createIfNotExists();
        } catch (final BlobStorageException e) {
            // already exists — safe to ignore
        }
        return containerClient;
    }

    /** Workload Identity auth: AKS production. Endpoint: https://{account}.blob.core.windows.net */
    public static BlobContainerClient forWorkloadIdentity(
            final String endpoint, final String containerName) {
        return new BlobServiceClientBuilder()
                .endpoint(endpoint)
                .credential(new DefaultAzureCredentialBuilder().build())
                .httpClientProvider(new JdkHttpClientProvider())
                .buildClient()
                .getBlobContainerClient(containerName);
    }

    // -----------------------------------------------------------------------
    // Path convention  (see metadata-convention.md)
    //   internal/{fileId}           — service's own files, not shared
    //   published/{topic}/{fileId}  — shared outbound files
    //   inbox/{topic}/{fileId}      — files received from another service
    // -----------------------------------------------------------------------

    public static String internalBlobName(final UUID fileId) {
        return "internal/" + fileId;
    }

    public static String publishedBlobName(final String topic, final UUID fileId) {
        return "published/" + topic + "/" + fileId;
    }

    public static String inboxBlobName(final String topic, final UUID fileId) {
        return "inbox/" + topic + "/" + fileId;
    }

    // -----------------------------------------------------------------------
    // UC1 — upload
    // BYOFS-1.3: correlation_id + filename metadata written atomically.
    // -----------------------------------------------------------------------

    public static UUID upload(final BlobContainerClient containerClient,
                              final String blobNamePrefix,
                              final String correlationId,
                              final String filename,
                              final byte[] content) {
        final UUID fileId = randomUUID();
        final String blobName = blobNamePrefix + "/" + fileId;
        containerClient
                .getBlobClient(blobName)
                .uploadWithResponse(
                        new BlobParallelUploadOptions(fromBytes(content))
                                .setMetadata(Map.of(
                                        "correlation_id", correlationId,
                                        "filename",       filename)),
                        null, null);
        return fileId;
    }

    // -----------------------------------------------------------------------
    // UC1 — download
    // Returns an InputStream the caller can pipe wherever needed — no heap materialisation.
    // Throws BlobNotFoundException if the blob does not exist.
    //
    // NOTE: openInputStream() NPEs against Azurite 3.x (local dev emulator).
    // If you use Azurite in tests, override this method to use downloadStreamWithResponse
    // with a PipedOutputStream/PipedInputStream pair, or pass in an OutputStream directly.
    // In production against real Azure Blob Storage this works correctly.
    // -----------------------------------------------------------------------

    public static InputStream download(final BlobContainerClient containerClient,
                                       final String blobName) {
        final var blobClient = containerClient.getBlobClient(blobName);
        if (!blobClient.exists()) {
            throw new BlobNotFoundException(blobName);
        }
        return blobClient.openInputStream();
    }

    // -----------------------------------------------------------------------
    // UC2.1 receiver — cross-container read, pipe to egress sink (no copy)
    //
    // sourceBlobUri must be a canonical URI — no SAS token.
    // The caller is responsible for handling the bytes (e.g. attach to email).
    //
    // Sink-buffering note: ByteArrayOutputStream is acceptable here only for
    // known-bounded files (XLSX reports).  For arbitrary/large blobs use a
    // PipedInputStream pair so bytes are never fully in heap simultaneously.
    //
    // In Azurite, connectionString must be set for cross-container access.
    // In production, the caller's managed identity needs Storage Blob Data
    // Reader on the owner's container (BYOFS-2.1 Bicep grant).
    // -----------------------------------------------------------------------

    public static byte[] streamCrossContainerToBytes(
            final String connectionString,
            final String sourceBlobUri) {
        final BlobClient blobClient;
        if (connectionString != null && !connectionString.isBlank()) {
            final URI uri = URI.create(sourceBlobUri);
            final String[] segments = uri.getPath().split("/");
            final String containerName = segments[2];
            final String blobPath = String.join("/",
                    java.util.Arrays.copyOfRange(segments, 3, segments.length));
            blobClient = new BlobServiceClientBuilder()
                    .connectionString(connectionString)
                    .httpClientProvider(new JdkHttpClientProvider())
                    .buildClient()
                    .getBlobContainerClient(containerName)
                    .getBlobClient(blobPath);
        } else {
            blobClient = new BlobClientBuilder()
                    .endpoint(sourceBlobUri)
                    .credential(new DefaultAzureCredentialBuilder().build())
                    .httpClientProvider(new JdkHttpClientProvider())
                    .buildClient();
        }
        final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        blobClient.downloadStreamWithResponse(
                buffer, new BlobRange(0, MAX_BLOB_BYTES),
                null, null, false, null, null);
        return buffer.toByteArray();
    }

    // -----------------------------------------------------------------------
    // UC2 receiver — server-side copy from a SAS URI
    // Bytes never transit the application server.
    // -----------------------------------------------------------------------

    public static void ingestFromSasUri(final BlobContainerClient containerClient,
                                        final String destinationBlobName,
                                        final String sourceUri,
                                        final String correlationId,
                                        final String filename) {
        containerClient
                .getBlobClient(destinationBlobName)
                .copyFromUrlWithResponse(
                        new BlobCopyFromUrlOptions(sourceUri)
                                .setMetadata(Map.of(
                                        "correlation_id", correlationId,
                                        "filename",       filename)),
                        null, null, null, null);
    }

    // -----------------------------------------------------------------------
    // UC2 owner — User Delegation SAS (read, 1-hour validity)
    // Never use account-key SAS — always use User Delegation SAS on AKS.
    // Requires the service principal to have Storage Blob Delegator role.
    // -----------------------------------------------------------------------

    public static String generateReadSas(final BlobServiceClient serviceClient,
                                         final String containerName,
                                         final String blobName) {
        final OffsetDateTime now = OffsetDateTime.now();
        final UserDelegationKey userDelegationKey =
                serviceClient.getUserDelegationKey(now.minusMinutes(5), now.plusHours(1));
        return serviceClient
                .getBlobContainerClient(containerName)
                .getBlobClient(blobName)
                .generateSas(
                        new BlobServiceSasSignatureValues(
                                now.plusHours(1),
                                new BlobSasPermission().setReadPermission(true)),
                        userDelegationKey);
    }
}
```

---

## Pattern notes

| Pattern | Key point |
|---|---|
| JDK HTTP transport | `JdkHttpClientProvider` avoids the Netty module conflict in WildFly 26. Safe to use on all runtimes. |
| `createIfNotExists` in try/catch | `BlobStorageException` (not `RuntimeException`) is what the SDK throws if the container already exists. |
| `openInputStream()` | Returns an `InputStream` the caller controls — no heap materialisation, no forced OutputStream dependency. **Caveat:** NPEs against Azurite 3.x. In services that use Azurite for dev/test (WildFly CDI pattern), use `downloadStreamWithResponse` instead — see `streaming.md`. |
| `BlobNotFoundException` | Replaces `Optional.empty()` — an `InputStream` cannot be wrapped in `Optional`, so the not-found case throws instead. Callers map this to a 404 at the API boundary. |
| Metadata atomicity | `BlobParallelUploadOptions.setMetadata(...)` and `BlobCopyFromUrlOptions.setMetadata(...)` write metadata in the same operation as the data — no separate `setMetadata` call needed. |
| User Delegation SAS | Requires `Storage Blob Delegator` RBAC on the storage account (not the container). Get the key once and reuse within its validity window if minting many SAS tokens. |
| `OffsetDateTime.now()` | Only acceptable here because this is a framework-free snippet. In WildFly services use `UtcClock` instead. |
| UC2.1 `streamCrossContainerToBytes` | Pass a canonical blob URI (no SAS). Connection string → parse Azurite path. No connection string → `DefaultAzureCredential` on the endpoint. `downloadStreamWithResponse` is mandatory — `openInputStream()` NPEs on Azurite. Buffering to `byte[]` is intentional here but only safe for bounded-size files. |
