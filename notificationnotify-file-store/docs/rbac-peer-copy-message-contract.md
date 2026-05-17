# BYOFS-2.2 — UC2 v7 Message Contract

Normative contract for the Service Bus message the **owner** publishes to trigger a
peer-to-peer blob copy under the BYOFS-2 (v7, RBAC-only) pattern.

---

## Context

In v6 (current), the owner mints a short-lived read-SAS URI and calls the receiver's
`POST /files/{fileId}` command endpoint. In v7 (BYOFS-2), the owner publishes a CPP
public event instead — no SAS is involved. The receiver holds `Storage Blob Data Reader`
RBAC on the owner's container (BYOFS-2.1) and performs `beginCopy` from the canonical URI.

---

## Event name

```
public.<owner-context>.<resource>-available
```

Examples:
- `public.mireportdata.warrant-report-available`
- `public.reference-data.reference-file-available`

The naming convention follows `public.<context>.<noun>-available` where the noun
describes the resource being transferred.

---

## Payload schema

```json
{
  "fileId":        "<UUID>",
  "blobUri":       "https://<account>.blob.core.windows.net/<container>/<blobPath>",
  "correlationId": "<UUID>",
  "filename":      "<original filename including extension>",
  "mediaType":     "<MIME type, e.g. application/vnd.openxmlformats-officedocument.spreadsheetml.sheet>"
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `fileId` | UUID string | Yes | Stable identifier for the blob in the owner's container |
| `blobUri` | string | Yes | Canonical URI — no SAS token, no query string |
| `correlationId` | UUID string | Yes | Business correlation ID; must match the value stored in the blob's `correlation_id` metadata |
| `filename` | string | Yes | Human-readable filename; must match the value stored in the blob's `filename` metadata |
| `mediaType` | string | No | MIME type to help the receiver validate or route the content |

### `blobUri` format

```
https://<storageAccount>.blob.core.windows.net/<container>/<blobPath>
```

- No `?` query string, no SAS parameters
- `container` is the per-service container name (e.g. `mi-reportdata`)
- `blobPath` follows the BYOFS-1.x convention: `published/<topic>/<fileId>`

---

## Owner responsibilities

1. Store the blob **before** publishing the event.
2. Set `correlation_id` and `filename` metadata atomically via
   `BlobParallelUploadOptions.setMetadata(...)` (BYOFS-1.3 convention).
3. The `blobUri` in the event must be the canonical URL — call `blobClient.getBlobUrl()`.
4. Do not include a SAS token in the URI.

---

## Receiver responsibilities

1. Subscribe to `public.<owner-context>.<event-name>` in `subscriptions-descriptor.yaml`.
2. Read `blobUri` with a null-safe get and skip if absent (owner may fire on failure).
3. Pass the canonical URI to `FileIngestor.ingest(storagePath, fileId, correlationId, filename, URI.create(blobUri))`.
4. Ensure the receiver's managed identity has `Storage Blob Data Reader` on the owner's
   container (BYOFS-2.1).

---

## Relation to v6

| | v6 (current) | v7 (BYOFS-2) |
|---|---|---|
| Transport | Command (`POST /files/{fileId}`) | CPP public event (Service Bus) |
| Source URI | SAS URI (signed, expiring) | Canonical URI (no SAS) |
| Auth on owner's blob | SAS read permission | RBAC `Storage Blob Data Reader` |
| Copy operation | `copyFromUrlWithResponse` | `beginCopy(BlobBeginCopyOptions).waitForCompletion()` |

---

## Example payload

```json
{
  "fileId":        "3a4b5c6d-0000-0000-0000-000000000001",
  "blobUri":       "https://cppstoragedev.blob.core.windows.net/mi-reportdata/published/live-reports/3a4b5c6d-0000-0000-0000-000000000001",
  "correlationId": "aaaabbbb-0000-0000-0000-000000000099",
  "filename":      "hmctsmi-warrants-2026-05-17.xlsx",
  "mediaType":     "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
}
```
