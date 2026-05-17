# UC2.1 Adopter Guide — Cross-container stream-to-sink

This guide is for teams implementing a UC2.1 receiver: a service that receives a
canonical blob URI from a peer and pipes the content directly to an egress sink
(email, HTTP response, transformation pipeline) **without** creating a copy in its
own container.

For the full event contract of the pilot integration see
[`stream-to-sink-event-contract.md`](stream-to-sink-event-contract.md).  For the download code see
[`streaming.md`](streaming.md) Pattern 3.

---

## 1. When to use UC2.1 (not UC2)

Use UC2.1 when:
- You need to act on the file content (send it, parse it, inspect it) but do not
  need to retain a durable copy in your own container.
- The file originates in another service's container and you have (or will have)
  `Storage Blob Data Reader` RBAC on that container.
- The processing is transient — once the email is sent or the bytes are forwarded,
  the data is not needed again.

Use UC2 instead when:
- Your service needs to own a copy for later retrieval, audit, or lifecycle management.
- The file is handed off in a workflow and ownership transfers to your service.

---

## 2. What you need

### 2a. Subscription configuration

Declare the event subscription in your service's
`{name}-event-processor/src/yaml/subscriptions-descriptor.yaml`:

```yaml
- name: <descriptive name> subscription
  events:
    - name: public.<producer-context>.<event-name>
      schema_uri: <schema URI>
  event_source_name: public.event.source
```

### 2b. Event handler

Write an `@Handles` method on your `@ServiceComponent(EVENT_PROCESSOR)`.  The
handler must:

1. Extract `blobUri` with a null-safe get and **skip silently** if absent — the
   producer fires the event for both success and failure outcomes.
2. Pass the canonical URI (no SAS) to your blob-reading code.
3. Not throw on transient blob-read failures unless you want the message to DLQ.

```java
@Handles("public.<producer-context>.<event-name>")
public void onReportAvailable(final JsonEnvelope event) {
    final JsonObject payload = event.payloadAsJsonObject();
    final String blobUri = payload.getString("blobUri", null);
    if (blobUri == null) {
        LOGGER.warn("event received without blobUri — skipping correlationId='{}'",
                event.metadata().id());
        return;
    }
    final UUID correlationId = event.metadata().id();
    final String recipientEmail = payload.getString("recipientEmail");
    final String subject = payload.getString("subject");
    final String filename = payload.getString("filename");
    blobFileEmailSender.sendEmailWithBlobAttachment(correlationId, blobUri, recipientEmail, subject, filename);
}
```

### 2c. Cross-container BlobClient

`BlobFileEmailSender` in `notificationnotify-event-processor` is the reference
implementation.  If you are wiring the same pattern in another WAR, copy the
`buildBlobClient(String sourceBlobUri)` method — it handles both Azurite and
production without any conditional test code in the caller:

```java
private BlobClient buildBlobClient(final String sourceBlobUri) {
    final String connectionString = azureBlobConfiguration.getConnectionString();
    if (connectionString != null && !connectionString.isBlank()) {
        final URI uri = URI.create(sourceBlobUri);
        final String[] segments = uri.getPath().split("/");
        final String containerName = segments[2];
        final String blobName = String.join("/", copyOfRange(segments, 3, segments.length));
        return new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient()
                .getBlobContainerClient(containerName)
                .getBlobClient(blobName);
    }
    return new BlobClientBuilder()
            .endpoint(sourceBlobUri)
            .credential(new DefaultAzureCredentialBuilder().build())
            .buildClient();
}
```

### 2d. JNDI and standalone.xml

Your WAR already has `azure.storage.connection-string` for its own container.  The
same JNDI key is read here — no additional JNDI entry is required.  In Azurite the
connection string covers all containers.

---

## 3. Production RBAC (BYOFS-2.1 — not yet started)

In production, `DefaultAzureCredential` resolves to the pod's Workload Identity.
That identity must hold `Storage Blob Data Reader` on the **producer's** container:

```
Producer storage account: <account>.blob.core.windows.net
Producer container:       <producer-context>
RBAC role:                Storage Blob Data Reader
Assignee:                 <receiver-context> managed identity
```

This Bicep module is tracked as BYOFS-2.1 and is not yet provisioned.  Until it
lands, the cross-container read will succeed only against Azurite.

---

## 4. Integration test

Write an IT that:
1. Seeds the producer container with a test blob (use `BlobStoreTestHelper` pointed
   at the producer container name, or call the Azure SDK directly).
2. Constructs your handler or `BlobFileEmailSender` equivalent with the Azurite
   connection string.
3. Invokes the handler with a synthetic event envelope containing the blob URI.
4. Asserts the expected outcome (email sent, bytes match, etc.).

Do not mock `BlobContainerClient` in IT tests — the whole point is to verify the
cross-container auth path against a real Azurite instance.

See `LiveReportEmailDeliveryIT` in `notificationnotify-file-store-core` as the
reference once that test is written (BYOFS-3.4).

---

## 5. SRE runbook — email not sent after report generation

**Symptom:** MI report generated successfully (status `COMPLETED` in viewstore) but
no email received.

**Step 1 — check the event was published**

```sql
SELECT payload FROM event_log
WHERE name = 'public.mireportdata.live-report-generated'
ORDER BY sequence_number DESC LIMIT 5;
```

If no rows: `LiveReportGenerationProcessor` did not publish the event.  Check
`mireportdata-event-processor` logs for `blobUri=null` or exception during
`objectToJsonValueConverter.convert`.

**Step 2 — check notification-notify consumed it**

Search notification-notify WildFly logs:
```
Streaming blob to email attachment correlationId='<id>'
```
If absent, the event was not consumed — check Artemis DLQ and
`subscriptions-descriptor.yaml` subscription wiring.

**Step 3 — check blob still exists**

```bash
az storage blob exists \
  --account-name <account> \
  --container-name mi-reportdata \
  --name published/live-reports/<filename> \
  --auth-mode login
```

If absent: the blob was deleted before the consumer read it (check lifecycle policy).
If present but 403: RBAC grant missing (BYOFS-2.1 not applied to this environment).

**Step 4 — check SMTP delivery**

Look for `Failed to send blob file email correlationId=` in notification-notify logs.
The root cause is in the wrapped exception message.  Common causes: SMTP host
unreachable, recipient address rejected, attachment too large.

**Thresholds for alerting:**

| Metric | Alert threshold |
|---|---|
| `public.mireportdata.live-report-generated` on DLQ | Any message — page immediately |
| Blob read 403 errors | Any — indicates missing BYOFS-2.1 RBAC grant |
| SMTP send failures | 3 in 5 minutes — check mail relay |
