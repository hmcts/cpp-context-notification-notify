# BYOFS Blob Metadata Convention

Every blob written to a BYO FileStore container **must** carry the two required
metadata keys defined below.  This metadata is the only per-file context the
platform tracks — there is no supplementary PostgreSQL table.

---

## Required Keys

| Key | Type | Description |
|---|---|---|
| `correlation_id` | Non-blank string | The causation chain from the envelope metadata — see below. |
| `filename` | Non-blank string | The human-meaningful original filename. UTF-8, ≤ 1024 bytes after Azure metadata encoding rules. |

Both keys are **mandatory on every upload**.  An upload that omits either key
violates BYOFS-1.3.

### `correlation_id` — the causation chain

In the CPP framework every command and event envelope carries a `causation` list:
the ordered sequence of command/event IDs that led to the current message.  Storing
the causation chain on every blob serves two purposes:

1. **Audit** — look up every blob created within a given transaction chain and
   correlate it with the originating command in logs and traces.
2. **Lifecycle tracking** — the foundation for future garbage-collection queries:
   "which blobs are still referenced by active transaction chains?"

**Serialisation format:** comma-separated UUID strings, in causation order.

```
a1b2c3d4-...,e5f6a7b8-...,c9d0e1f2-...
```

**Fallback:** if the envelope's causation list is empty (the originating command),
use the envelope's own `id` as the sole value.

---

## Azure Blob Metadata Constraints

- **Lowercase snake_case keys.**  Azure normalises all keys to lowercase on read.
  Use `correlation_id`, `filename`, `mime_type` — never camelCase or PascalCase.
- **ASCII-safe values.**  Values are transmitted as HTTP headers.  Non-ASCII
  characters must be percent-encoded or Base64-encoded.
- **Total metadata ≤ 8 KB.**  Combined size of all key/value pairs.
- **No leading/trailing whitespace.**  Azure rejects keys or values with leading/trailing spaces.

---

## Optional Reserved Keys

If used, follow the names below so platform tooling and log queries remain
consistent across services.

| Key | Description |
|---|---|
| `content_sha256` | Hex-encoded SHA-256 of the blob content, for integrity verification. |
| `source_component` | Logical name of the component that wrote the blob (e.g. `notificationnotify-event-processor`). |
| `created_by_subject_oid` | Entra ID object ID of the user or service principal that triggered the upload. |

---

## Java SDK Reference

### Extracting `correlation_id` from envelope metadata

See [correlation-id.md](correlation-id.md) for the derivation pattern, per-caller values, and the identity-as-path and name-based UUID patterns.

### Setting metadata atomically during upload

Use `BlobParallelUploadOptions` to set metadata in the same operation as the
upload.  Setting metadata in a separate `setMetadata()` call after `upload()`
introduces a window where the blob exists without metadata.

```java
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.options.BlobParallelUploadOptions;

import java.util.Map;
import static java.util.Map.of;

final BlobParallelUploadOptions uploadOptions =
        new BlobParallelUploadOptions(binaryData)
                .setMetadata(of(
                        "correlation_id", correlationIdFrom(envelope),
                        "filename",       filename));

blobClient.uploadWithResponse(uploadOptions, null, Context.NONE);
```

---

## Audit Trail — KQL Queries

Azure Storage Diagnostic Logs write one row per operation to `StorageBlobLogs` in
Log Analytics.

### Find all upload operations for a given correlation ID

```kql
StorageBlobLogs
| where OperationName == "PutBlob"
| where ObjectKey contains "<your-correlation-id>"
| project TimeGenerated, ObjectKey, CallerIpAddress, AuthenticationType
| order by TimeGenerated desc
```

### Retrieve metadata for a blob by name

```java
final BlobClient blobClient = containerClient.getBlobClient(blobName);
final Map<String, String> metadata = blobClient.getProperties().getMetadata();
final String correlationId = metadata.get("correlation_id");
final String filename = metadata.get("filename");
```

---

## Lint Check — BYOFS-1.6

Every BYOFS upload must demonstrate that both `correlation_id` and `filename` are
set.  In unit tests, capture the `BlobParallelUploadOptions` via `ArgumentCaptor`
and assert both keys on the captured metadata map:

```java
verify(blobClient).uploadWithResponse(uploadOptionsCaptor.capture(), isNull(), eq(Context.NONE));

final Map<String, String> metadata = uploadOptionsCaptor.getValue().getMetadata();
assertThat(metadata.get("correlation_id"), is(not(blankOrNullString())));
assertThat(metadata.get("filename"), is(not(blankOrNullString())));
```
