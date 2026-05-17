# BYOFS-2.3 — Metadata carry-forward convention (UC2 v7)

Defines how the BYOFS-1.3 metadata (`correlation_id` + `filename`) is preserved across
a v7 `beginCopy` peer-to-peer transfer.

---

## The problem

Azure's `beginCopy` (server-side async copy) does **not** automatically propagate source
blob metadata to the destination. If you call `blobClient.beginCopy(sourceUri)` without
supplying metadata options, the destination blob will have no `correlation_id` or
`filename` metadata, violating the BYOFS-1.3 convention.

---

## The rule

The receiver **must** set metadata atomically on the destination blob via
`BlobBeginCopyOptions.setMetadata(...)`:

```java
new BlobBeginCopyOptions(sourceUri.toString())
        .setMetadata(Map.of(
                "correlation_id", correlationId.toString(),
                "filename",       filename))
```

The `correlationId` and `filename` values come from the triggering event payload
(see `rbac-peer-copy-message-contract.md`). They must match the values the owner set
on the source blob.

---

## Reference implementation

`FileIngestor.ingest()` in `notificationnotify-file-store-core` implements this
convention — the `setMetadata` call is inside `beginCopy(BlobBeginCopyOptions)`.

---

## Why the receiver sets the metadata (not the source)

- The receiver controls the destination blob lifecycle and owns the metadata namespace
  in its container.
- Copying source metadata blindly would carry over any extra keys the owner stored,
  potentially polluting the receiver's metadata schema.
- Audit queries (see `metadata-convention.md`) target the destination blob; the receiver
  setting metadata explicitly makes audit reliable regardless of source metadata state.

---

## Verification

`FileIngestorIT.shouldSetCorrelationIdMetadataOnIngestedBlob` and
`FileIngestorIT.shouldSetFilenameMetadataOnIngestedBlob` assert that both metadata keys
are present on the destination blob after a `beginCopy` against Azurite.
