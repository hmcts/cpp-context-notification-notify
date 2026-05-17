# BYO FileStore — UC2.1 message contract: `public.mireportdata.live-report-generated`

Normative contract for the CPP event used in the BYOFS-3.6 pilot
(`cpp-context-mi-reportdata → cpp-context-notification-notify`).

---

## Overview

| Property | Value |
|---|---|
| Event name | `public.mireportdata.live-report-generated` |
| Producer | `cpp-context-mi-reportdata` / `LiveReportGenerationProcessor` |
| Consumer(s) | `cpp-context-notification-notify` / `NotificationNotifyPublicEventProcessor.liveReportGenerated` |
| Transport | CPP Artemis JMS (`public.event.source`) |
| Pattern | UC2.1 — cross-container stream-to-sink; no receiver-side blob copy |
| Schema URI | `http://cpp.moj.gov.uk/mireportdata/json/schemas/event/public.mireportdata.live-report-generated.json` |

---

## Payload fields

| Field | Type | Required | Notes |
|---|---|---|---|
| `blobUri` | string (URI) | Conditional | Canonical blob URI — **no SAS token**. Present when generation succeeded; absent when it failed. Consumer must not throw if absent — skip silently (see Skip behaviour below). |
| `filename` | string | Yes (when `blobUri` present) | Report filename, e.g. `hmctsmi-warrants-2025-01-15T10:30:00.xlsx`. Used as the email attachment filename. |
| `recipientEmail` | string | Yes (when `blobUri` present) | Destination email address. |
| `subject` | string | Yes (when `blobUri` present) | Email subject line. |
| `downloadableLink` | string (URI) | No | SAS-signed URI for direct browser download. Distinct from `blobUri` — carries a `?sv=...&sig=...` query string. Used by MI report viewers; not consumed by the email handler. |
| `generatedAt` | string (ISO-8601) | No | UTC timestamp when the report was generated. |
| `properties` | object | No | Report metadata key-value pairs extracted from the original generation request. |

---

## `blobUri` vs `downloadableLink`

`blobUri` is a clean canonical URI:

```
https://{account}.blob.core.windows.net/{container}/{blobPath}
```

It carries no credentials. The consumer authenticates with `Storage Blob Data Reader`
RBAC on the producer's container (production) or connection-string auth (Azurite).

`downloadableLink` is `blobUri + "?" + sasToken`. It is safe for browser links or
services without Azure RBAC. **Do not** pass `downloadableLink` as the source URI
to `BlobFileEmailSender` — the trailing SAS query string causes
`BlobClientBuilder.endpoint()` to throw an invalid-endpoint exception.

---

## Skip behaviour

The consumer **must** skip silently when `blobUri` is absent, logging a warning but
not throwing. `LiveReportGenerationProcessor` fires this event for both successful
and failed generations — a failed generation carries status and message but no blob.
Throwing on a missing `blobUri` would DLQ a message that requires no action.

```java
final String blobUri = payload.getString("blobUri", null);
if (blobUri == null) {
    LOGGER.warn("live-report-generated received without blobUri — skipping correlationId='{}'",
            event.metadata().id());
    return;
}
```

---

## Consumer auth — dual-mode (`BlobFileEmailSender.buildBlobClient`)

| Environment | Auth | Notes |
|---|---|---|
| Azurite (connection string set) | `BlobServiceClientBuilder.connectionString(...)` | URI path parsed: `/devstoreaccount1/{container}/{blobPath...}` — `segments[2]` = container, `segments[3+]` = blob path. |
| Production (no connection string) | `BlobClientBuilder.endpoint(blobUri).credential(DefaultAzureCredential)` | Workload Identity resolves automatically on AKS. Requires BYOFS-2.1 RBAC grant (see below). |

**BYOFS-2.1 dependency (production):** `notification-notify`'s managed identity must be
granted `Storage Blob Data Reader` on `mi-reportdata`'s blob container.  This Bicep
module is tracked in BYOFS-2.1 and is **not yet provisioned** in production.  The
pilot runs against Azurite only until that grant lands.

---

## Example payload

```json
{
  "blobUri":   "https://cppstorage.blob.core.windows.net/mi-reportdata/published/live-reports/hmctsmi-warrants-2025-01-15T10:30:00.xlsx",
  "filename":  "hmctsmi-warrants-2025-01-15T10:30:00.xlsx",
  "recipientEmail": "mi-reports@hmcts.net",
  "subject":   "Warrants Report — 2025-01-15",
  "downloadableLink": "https://cppstorage.blob.core.windows.net/mi-reportdata/published/live-reports/hmctsmi-warrants-2025-01-15T10:30:00.xlsx?sv=2021-06-08&se=...&sig=...",
  "generatedAt": "2025-01-15T10:31:42Z"
}
```

---

## Subscription configuration

`cpp-context-notification-notify` declares this subscription in
`notificationnotify-event/notificationnotify-event-processor/src/yaml/subscriptions-descriptor.yaml`:

```yaml
- name: public mireportdata live report subscription
  events:
    - name: public.mireportdata.live-report-generated
      schema_uri: http://cpp.moj.gov.uk/mireportdata/json/schemas/event/public.mireportdata.live-report-generated.json
  event_source_name: public.event.source
```
