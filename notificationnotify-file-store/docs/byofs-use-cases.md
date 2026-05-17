# BYO FileStore — Use Cases

**Status legend:** ✅ Done · ❌ Not started · ⚠️ Partial / pending confirmation

---

## UC1 — Self-contained upload

### What it does

The owning service receives file bytes (e.g. from an email attachment or a user upload) and stores them directly in its own Azure Blob container under the `internal/{fileId}` prefix. Metadata (`correlation_id` and `filename`) is written atomically via `BlobParallelUploadOptions.setMetadata(...)`. No other service is involved; the file never leaves the owning container via a URL share.

**Implementation pattern:** `FileStorer.store(StoragePath.internal(), correlationId, filename, bytes)` — returns a `fileId`.

### When it applies

A service is the originator of the file content and does not need to share it with another service at upload time. Retrieval is purely internal to the same service.

### Implementation status

| Service | Status | Notes |
|---|---|---|
| `cpp-context-notification-notify` | ✅ Done | `PocaEmailsTask` delegates to `FileStorer`. `FileStorerIT` (5 tests) + `FileRetrieverIT` (4 tests) pass against Azurite. |
| `cpp-context-mi-reportdata` | ✅ Done | `MiExtractBlobClientService.upload()` uses `uploadWithResponse` with atomic metadata. 5 unit tests pass. |
| `cpp-context-reference-data` | ✅ Done | `ElinksJudiciaryLoadAzureService` uses `uploadWithResponse` with atomic metadata. All tests pass. |
| `cpp-context-sjp` | ✅ Done | `SjpServiceFileInterceptor` → `FileStorer`; query API resources → `BlobContainerClient` + `StreamingOutput` (no heap materialisation); `TransparencyReportRequestedProcessor` + `PressTransparencyReportRequestedProcessor` → `FileStorer.store(published("sdg-payloads"), ...)` + `SasUriGenerator.generateReadUri()`. **`TransparencyReportIT.shouldGenerateTransparencyPDFReports` and `PressTransparencyReportIT.shouldGeneratePressTransparencyPDFReports` pass** against Azurite. Full `runIntegrationTests.sh` passed: 253 tests, 0 failures, 11 skipped (2026-05-17). |
| `cpp-context-system-doc-generator` | ✅ Done | `byo-file-store` branch. `PayloadRetrievalService` reads uploaded payload from `internal/` via `FileRetriever` and re-stores the extracted template payload via `FileStorer`. `RenderDocumentDelegate.fetchPayload()` reads the stored template payload from `internal/` via `FileRetriever`. `FileRetriever` uses `downloadStreamWithResponse` for Azurite compatibility. Full `runIntegrationTests.sh` passed: 78 tests, 0 failures, 0 skipped (2026-05-17). |

### Known gaps

None for the five services above.

---

## UC2 — Peer-to-peer SAS transfer (ownership transfer)

### What it does

The *owner* service generates a short-lived read-SAS URL on an existing blob and sends it (via a command or event) to the *receiver* service. The receiver performs a server-side blob copy from the SAS URL into its own container using `BlobCopyFromUrlOptions(sourceUri).setMetadata(...)` + `blobClient.copyFromUrlWithResponse(options, null, NONE)`. File bytes never transit the application server.

SAS generation must use User Delegation SAS (`generateUserDelegationSas`) — never account-key SAS.

### When it applies

One service originates a file and a second service needs a durable copy in its own container (e.g. after a workflow hand-off or cross-context event). The sender mints the SAS; the receiver pulls the bytes directly from Azure.

### Implementation status — owner side (SAS minting)

| Service | Status | Notes |
|---|---|---|
| `cpp-context-mi-reportdata` | ✅ Done | `LiveReportBlobClientService` updated to `generateUserDelegationSas`. Full `runIntegrationTests.sh` passed: 314 tests, 0 failures, 11 skipped (2026-05-17). |
| `cpp-context-reference-data` | ✅ Done | `publish-reference-data-file` command/endpoint/DRL; `ReferenceDataFilePublishedEventProcessor` generates public event with blob URI. Full `runIntegrationTests.sh` passed: 862 tests, 0 failures, 4 skipped (2026-05-17). |
| `cpp-context-system-doc-generator` | ✅ Done | `byo-file-store` branch. `RenderDocumentDelegate.storeDocument()` routes all 8 SJP transparency/press-report templates to `FileStorer.store(published("sjp-docs"), ...)`. `SjpDocumentPublisher.augmentForSjp()` reads the resulting `blobFileId` from the payload and generates a read-SAS URI via `SasUriGenerator` — bytes no longer transit the app server via PostgreSQL. `public.systemdocgenerator.events.document-available` carries `blobFileId`+`sourceUri`. Full `runIntegrationTests.sh` passed: 78 tests, 0 failures, 0 skipped (2026-05-17). |
| All other UC2 owner services | ⚠️ Pending | Full list awaiting TA team confirmation. Any service still calling account-key `generateSas()` must migrate. |

### Implementation status — receiver side (`POST /files/{fileId}` ingest endpoint)

| Service | Status | Notes |
|---|---|---|
| `cpp-context-notification-notify` | ✅ Done | `FileIngestor` (file-store-core), `IngestFileCommandHandler`, RAML `POST /files/{fileId}`, JSON schema/example, DRL rule (`System Users`). `FileIngestorIT` (4 tests) passes against Azurite. |
| `cpp-context-sjp` | ✅ Done | `FileIngestor`, `IngestFileCommandHandler`, RAML `POST /files/{fileId}`, JSON schema/example, COMMAND_API DRL rule (`System Users`), controller messaging RAML entry, COMMAND_CONTROLLER DRL rule, `NoActionController.ingestFile()` pass-through. Full unit + IT suites against Azurite pass. End-to-end pipeline (COMMAND_API → JMS bridge → COMMAND_CONTROLLER → COMMAND_HANDLER) confirmed working via `TransparencyReportIT` + `PressTransparencyReportIT`. |
| All other UC2 receiver services | ⚠️ Pending | Full list awaiting TA team confirmation. |

### Known gaps

- **`cp-file-service` migration:** All services still using `cp-file-service` for file exchange must migrate to the UC2 SAS pattern. Known affected services: `cpp-context-progression`, `cpp-context-resulting`, `cpp-context-correspondence`, `cpp-context-hearing-nows`, and others. Full list pending TA confirmation.

---

## UC2.1 — Cross-container stream-to-sink (read-and-process, no receiver container)

### What it does

The *owner* service uploads a blob to its own container and publishes a CPP event
carrying the **canonical blob URI** (no SAS token) plus routing metadata (recipient
email, filename, subject).  The *receiver* service opens a cross-container
`BlobClient` pointed at that URI and pipes the bytes directly to an egress sink —
in the pilot, an SMTP email attachment via JavaMail.  No copy of the blob is created
in the receiver's container.

**Implementation pattern:** `BlobFileEmailSender.sendEmailWithBlobAttachment(correlationId, blobUri, recipientEmail, subject, filename)`

See [`uc21-message-contract.md`](uc21-message-contract.md) for the full event shape and
auth configuration, and [`streaming.md`](streaming.md) Pattern 3 for the download code.

### When it applies

The receiver needs to act on the file content (send it, transform it, inspect it)
but does not need to retain a durable copy in its own storage.  A persistent copy
would add storage cost and lifecycle-management overhead with no benefit.

### UC2.1 vs UC2

| | UC2 | UC2.1 |
|---|---|---|
| Receiver container | Required (copy lands there) | Not needed |
| Auth on owner's container | Read SAS (v6) or RBAC Reader (v7) | RBAC Reader (always) |
| Message carries | SAS URI | Canonical blob URI (no SAS) |
| Bytes transit receiver pod | No (server-side copy) | Yes (download → sink) |
| Receiver retains a copy | Yes | No |

### Implementation status

| Service | Role | Status | Notes |
|---|---|---|---|
| `cpp-context-mi-reportdata` | Owner (publisher) | ⚠️ In progress | `LiveReportGenerationProcessor` publishes `public.mireportdata.live-report-generated` with `blobUri` + routing fields. Unit tests pass. IT pending (`runIntegrationTests.sh`). |
| `cpp-context-notification-notify` | Receiver (sink) | ⚠️ In progress | `NotificationNotifyPublicEventProcessor.liveReportGenerated` → `BlobFileEmailSender.sendEmailWithBlobAttachment`. Subscription in `subscriptions-descriptor.yaml`. Unit tests pass. IT pending. |

### Known gaps

- **BYOFS-2.1 (not started):** `notification-notify`'s managed identity needs
  `Storage Blob Data Reader` on `mi-reportdata`'s container.  Without this, the
  production cross-container read will fail with 403.  The pilot can only run against
  Azurite until this Bicep module lands.

---

## UC3 — Stream or read a blob without storing a persistent copy

### What it does

A service reads or streams a blob (from its own container or a peer's) without materialising the full content in heap or writing it to a second storage location. The two common forms:

- **HTTP streaming:** serve a blob directly as an HTTP response body using JAX-RS `StreamingOutput` — bytes flow from Azure to the HTTP client via the JVM I/O buffer only.
- **Doc-gen callback:** a requesting service mints a write-SAS on a pre-agreed blob path and passes it to SDG; SDG writes the generated document directly to that path; SDG raises an Event Grid event; the requesting service reads/streams the result without creating a separate copy.

In both cases bytes never accumulate in heap. See [streaming.md](streaming.md) for the implementation patterns.

### When it applies

The service needs to read or serve file content where creating a durable copy in a separate container is unnecessary — the blob already exists at a reachable path, or the result arrives at a pre-agreed path via a doc-gen callback.

### Implementation status

| Service | Status | Notes |
|---|---|---|
| `cpp-context-sjp` | ✅ Done | `byo-file-store` branch. Transparency report and press transparency report query resources replaced with `BlobContainerClient` + JAX-RS `StreamingOutput` — PDF bytes flow directly from Azure to the HTTP client; no heap buffering. Full `runIntegrationTests.sh` passed: 253 tests, 0 failures, 11 skipped (2026-05-17). |
| Doc-gen callback services | ❌ Not started | Write-SAS provisioning + Event Grid subscription wiring. All affected services pending TA confirmation. |

### Doc-gen callback sub-pattern

| Sub-task | Status |
|---|---|
| Write-SAS provisioning (mint write-SAS before triggering SDG) | ❌ Not started |
| Event Grid subscription wiring + SAS validation | ❌ Not started |

---

## Platform deliverables

The following BYOFS items are architecture team responsibility and are **not** tracked in individual service repos. Full status in [`byofs-implementation-gaps.md`](byofs-implementation-gaps.md).

| Ticket | Title | Status |
|---|---|---|
| BYOFS-1.1 | Bicep module — per-service Azure Blob container + RBAC | ❌ Not started |
| BYOFS-1.2 | Bicep module — Azure Storage Lifecycle Management policy (TTL) | ❌ Not started |
| BYOFS-1.3 | Metadata convention spec — `correlation_id` + `filename` on every blob | ✅ Done — `docs/metadata-convention.md` |
| BYOFS-1.4 | Workload Identity wiring guide for adopting services | ✅ Done — `docs/workload-identity-guide.md` |
| BYOFS-1.5 | Reference example — direct Azure SDK usage with metadata + lifecycle | ✅ Done — `docs/reference-example.md` |
| BYOFS-1.6 | Integration test against Azurite | ✅ Done — `FileStorerIT`, `FileRetrieverIT`, `FileIngestorIT` in `notificationnotify-file-store-core` |
| BYOFS-1.7 | Adopter onboarding guide + SRE runbook | 🟡 Partial — `azure-blobstore-migration.md` covers onboarding; production SRE runbook not started |
