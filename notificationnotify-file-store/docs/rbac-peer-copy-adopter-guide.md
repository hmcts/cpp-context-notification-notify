# BYOFS-2.6 — Adopter Guide: UC2 v7 RBAC peer-to-peer copy

This guide is for teams implementing either side of a UC2 v7 ownership transfer: a
service that stores a blob and triggers a copy into a peer's container, or a service
that receives such a trigger and copies the blob into its own container.

For the event contract see [`rbac-peer-copy-message-contract.md`](rbac-peer-copy-message-contract.md).
For metadata requirements see [`rbac-peer-copy-metadata.md`](rbac-peer-copy-metadata.md).

---

## 1. When to use UC2 v7 (not v6 or UC2.1)

| Scenario | Pattern |
|---|---|
| Receiver needs a durable copy; SAS token acceptable | UC2 v6 (current) |
| Receiver needs a durable copy; RBAC-only preferred | **UC2 v7 (this guide)** |
| Receiver processes content transiently; no copy needed | UC2.1 (`stream-to-sink-adopter-guide.md`) |

Use UC2 v7 when the receiving service must own a copy for later retrieval, audit, or
lifecycle management, **and** you want to avoid SAS token management (minting, expiry,
rotation).

---

## 2. What you need

### 2a. RBAC grant (BYOFS-2.1 — infrastructure prerequisite)

Before any production traffic can flow, the receiver's managed identity must hold
`Storage Blob Data Reader` on the **owner's** container:

```
Owner storage account: <account>.blob.core.windows.net
Owner container:       <owner-context>
RBAC role:             Storage Blob Data Reader
Assignee:              <receiver-context> managed identity
```

This is a Bicep `roleAssignment` resource tracked as BYOFS-2.1.  Until it is provisioned,
the copy will succeed only against Azurite (which uses a shared connection string).

### 2b. Owner side — store and publish

1. Upload the blob via `FileStorer.store(StoragePath.published(topic), correlationId, filename, bytes)`.
   This returns a `fileId`.
2. Retrieve the canonical URI: `blobContainerClient.getBlobClient(StoragePath.published(topic).blobName(fileId)).getBlobUrl()`.
3. Publish a CPP public event following the contract in `rbac-peer-copy-message-contract.md`.
   Include `fileId`, `blobUri` (canonical, no SAS), `correlationId`, `filename`.

### 2c. Receiver side — subscribe and copy

Declare the event subscription in your
`{name}-event-processor/src/yaml/subscriptions-descriptor.yaml`:

```yaml
- name: <owner context> <resource> available subscription
  events:
    - name: public.<owner-context>.<resource>-available
      schema_uri: <schema URI>
  event_source_name: public.event.source
```

Write an `@Handles` method on your `@ServiceComponent(EVENT_PROCESSOR)`:

```java
@Handles("public.<owner-context>.<resource>-available")
public void onResourceAvailable(final JsonEnvelope event) {
    final JsonObject payload = event.payloadAsJsonObject();
    final String blobUri = payload.getString("blobUri", null);
    if (blobUri == null) {
        LOGGER.warn("event received without blobUri — skipping correlationId='{}'",
                event.metadata().id());
        return;
    }
    final UUID fileId = UUID.fromString(payload.getString("fileId"));
    final UUID correlationId = UUID.fromString(payload.getString("correlationId"));
    final String filename = payload.getString("filename");
    fileIngestor.ingest(StoragePath.internal(), fileId, correlationId, filename, URI.create(blobUri));
}
```

`FileIngestor` is provided by the `{name}-file-store-core` CDI module (see
`azure-blobstore-migration.md` for wiring). It calls
`beginCopy(BlobBeginCopyOptions).waitForCompletion()` and sets `correlation_id` and
`filename` metadata atomically on the destination blob.

### 2d. JNDI configuration

No additional JNDI entries are needed for the RBAC path.  In Azurite the existing
`azure.storage.connection-string` entry covers cross-container access.  In production,
the connection string is blank and `DefaultAzureCredential` resolves to the pod's
Workload Identity, which must hold the BYOFS-2.1 RBAC grant.

---

## 3. Integration test

Write an IT that:
1. Uploads a blob to a "source" container via the Azure SDK (or `BlobStoreTestHelper`
   pointed at the source container).
2. Constructs a `FileIngestor` with `setField` pointing at the destination container.
3. Calls `fileIngestor.ingest(...)` with the canonical source URL.
4. Asserts that the destination blob exists and has `correlation_id` / `filename` metadata.

See `FileIngestorIT` in `notificationnotify-file-store-core` as the reference.
Azurite's shared connection string gives the test client access to both containers, so
no RBAC grant is needed in the test environment.

---

## 4. SRE runbook — file not received after owner published event

**Symptom:** Owner published the event successfully but receiver's blob is absent.

**Step 1 — check the event was consumed**

Search receiver WildFly logs for:
```
Ingested blob '<blobName>' sourceUri='<uri>'
```
If absent: check Artemis DLQ for `public.<owner-context>.<event-name>` and verify
`subscriptions-descriptor.yaml` is wired correctly.

**Step 2 — check the source blob still exists**

```bash
az storage blob exists \
  --account-name <account> \
  --container-name <owner-context> \
  --name published/<topic>/<fileId> \
  --auth-mode login
```

If absent: source blob was deleted before the copy completed (check lifecycle policy TTL).

**Step 3 — check RBAC**

If the blob exists but the copy fails with a 403:

```bash
az role assignment list \
  --scope "/subscriptions/<sub>/resourceGroups/<rg>/providers/Microsoft.Storage/storageAccounts/<account>/blobServices/default/containers/<owner-context>" \
  --query "[?principalName=='<receiver-identity>']"
```

If the BYOFS-2.1 role assignment is absent, apply it via the Bicep module.

**Thresholds for alerting:**

| Metric | Alert threshold |
|---|---|
| `public.<owner-context>.<event>` on DLQ | Any message — page immediately |
| Blob copy 403 errors in receiver logs | Any — indicates missing BYOFS-2.1 RBAC grant |
| `beginCopy` timeout (copy stalled) | 1 in 30 minutes — check Azure Storage health |
