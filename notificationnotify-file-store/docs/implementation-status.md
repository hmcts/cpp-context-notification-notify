# BYO FileStore — Implementation Status

Tracks what has been built in `notificationnotify-file-store` against the v6 design
and BYOFS-1 ticket plan.

**`cpp-context-notification-notify` is the canonical reference implementation.**
Other teams migrating to BYO FileStore should use this context as the model —
copy the module structure and adjust package names and coordinates for their own
context.

**Design reference:** `pe_arch_design_docs/mbd_filestore/byo-filestore-azure-native-design_v6.md`
**Branch:** `byo-file-store` (in `cpp-context-notification-notify`)

---

## v6 ticket plan status (BYOFS-1 epic)

| Ticket | Title | Points | Status | Notes |
|---|---|---|---|---|
| **BYOFS-1.1** | Bicep IaC: per-service container + RBAC | 5 | ❌ Not started | TA/infra team (suggested: `cpp-aks-deploy`). Needs: one container per service; `Storage Blob Data Contributor` on own container; `Storage Blob Delegator` on storage account (UC2 owners); `Storage Blob Data Reader` on peer containers (UC2/UC3). See `byofs-implementation-gaps.md` for full spec. |
| **BYOFS-1.2** | Bicep IaC: Lifecycle Management policy | 3 | ❌ Not started | TA/infra team. Depends on BYOFS-1.1. `managementPolicies` resource, prefix-filtered: `internal/` 30d, `published/` 90d, `inbox/` 14d (TTLs pending TA confirmation). See `byofs-implementation-gaps.md`. |
| **BYOFS-1.3** | Metadata convention: `correlation_id` + `filename` | 2 | ✅ Done | Spec at `metadata-convention.md`. All callers must store both keys. |
| **BYOFS-1.4** | Workload Identity wiring guide | 2 | ✅ Done | Guide at `workload-identity-guide.md`. |
| **BYOFS-1.5** | Reference example: direct SDK usage (≤200 LoC) | 5 | ✅ Done | `docs/reference-example.md` — standalone Java class (~110 LoC): connection-string + Workload Identity auth, UC1 upload/download, UC2 server-side copy, UC2 User Delegation SAS. No CDI, no JNDI. |
| **BYOFS-1.6** | Integration test against Azurite | 3 | ✅ Done | Azurite via `cpp-azurite` Docker container. `BlobStoreTestHelper` in `notificationnotify-file-store-test-utils`; unit tests in `-core` and `-test-utils` cover all CDI wiring and path-prefix logic. |
| **BYOFS-1.7** | Onboarding guide + SRE runbook | 3 | 🟡 Partial | `azure-blobstore-migration.md` covers onboarding and dev setup. Production SRE runbook (alert thresholds, on-call steps, connection string rotation) not started. |

**Total: 4 done / 1 partial / 2 not started out of 7 stories**

---

## CDI wiring (in notificationnotify-file-store-core)

| Artefact | Status | Notes |
|---|---|---|
| `AzureBlobConfiguration` | ✅ Done | JNDI-backed; reads connection-string, endpoint, container-name |
| `AzureBlobContainerClientProducer` | ✅ Done | `@Dependent` workaround for Weld + final class (WELD-001410); `createIfNotExists()` in `@PostConstruct` |
| `StoragePath` | ✅ Done | Static factory for `internal()`, `published(topic)`, `inbox(topic)`; `blobName(UUID)` builds the full path |
| `FileStorer` | ✅ Done | UC1 upload — `BlobParallelUploadOptions` with atomic `correlation_id` + `filename` metadata; returns generated `fileId` |
| `FileRetriever` | ✅ Done | UC1 download — `downloadStreamWithResponse` with 1 GB range; returns `Optional<byte[]>` |
| `FileIngestor` | ✅ Done | UC2 receiver — server-side copy via `BlobCopyFromUrlOptions` + `copyFromUrlWithResponse`; sets metadata atomically |
| JDK HTTP transport | ✅ Done | `azure-core-http-jdk-httpclient` — avoids WildFly Netty classpath conflict |
| Connection string auth (Azurite) | ✅ Done | Used in dev/test |
| DefaultAzureCredential fallback | ✅ Done | If connection-string blank, uses `DefaultAzureCredentialBuilder` — picks up Workload Identity on AKS |

---

## Test infrastructure

| Artefact | Status | Notes |
|---|---|---|
| `BlobStoreTestHelper` | ✅ Done | `upload`, `download`, `exists`, `delete` against path-prefix convention; created via `forConnectionStringAndContainer` |
| Unit tests for `BlobStoreTestHelper` | ✅ Done | `BlobStoreTestHelperTest` in `notificationnotify-file-store-test-utils` |
| Unit tests for `StoragePath` | ✅ Done | `StoragePathTest` in `notificationnotify-file-store-core` |
| Unit tests for CDI producer | ✅ Done | `AzureBlobContainerClientProducerTest`, `AzureBlobConfigurationTest` |
| Unit tests for `FileStorer` | ✅ Done | `FileStorerTest` — atomic metadata, blob name, returned fileId |
| Unit tests for `FileRetriever` | ✅ Done | `FileRetrieverTest` — found, not-found, download stream |
| Unit tests for `FileIngestor` | ✅ Done | `FileIngestorTest` — copy target path, metadata, exception propagation |
| `FileStorerIT` | ✅ Done | 5 IT tests against Azurite — path prefix, `correlation_id`, `filename`, published prefix, distinct fileIds |
| `FileRetrieverIT` | ✅ Done | 4 IT tests against Azurite — retrieve content, not-found empty, published prefix, round-trip with FileStorer |
| `FileIngestorIT` | ✅ Done | 4 IT tests against Azurite — blob existence, `correlation_id`, `filename`, content round-trip |

---

## JNDI entries in standalone.xml

| Artefact | Status | Notes |
|---|---|---|
| `notificationnotify-event-processor` JNDI entries | ✅ Done | On `byo-file-store` branch of `cpp-developers-docker` |
| `notificationnotify-service` JNDI lookups | ✅ Done | On `byo-file-store` branch of `cpp-developers-docker` |

---

## Documentation

| Artefact | Status | Notes |
|---|---|---|
| `docs/azure-blobstore-migration.md` | ✅ Done | Comprehensive migration guide — notification-notify as canonical reference |
| `docs/jndi.md` | ✅ Done | JNDI config reference; per-environment values; global shortcut pattern; onboarding template |
| `docs/streaming.md` | ✅ Done | Pattern 1 (HTTP response) and Pattern 2 (PipedInputStream) |
| `docs/metadata-convention.md` | ✅ Done | BYOFS-1.3 metadata key spec with KQL audit queries |
| `docs/correlation-id.md` | ✅ Done | Per-caller `correlation_id` patterns |
| `docs/workload-identity-guide.md` | ✅ Done | AKS Workload Identity wiring guide |
| `docs/authentication.md` | ✅ Done | Dual-mode auth reference; Pattern 1 (single container) and Pattern 2 (multi-container CDI qualifier) |
| `docs/byofs-use-cases.md` | ✅ Done | Plain-English UC1/UC2/UC3 summaries with per-service implementation status |
| `docs/design-decisions.md` | ✅ Done | Design decisions resolved by v6; deferred items |
| `docs/context-migration-status.md` | ✅ Done | Cross-context migration tracker — all 31 CPP contexts with file storage usage |
| `docs/non-standard-changes.md` | ✅ Done | Non-standard changes made in contexts we don't own (SJP, system-doc-generator, mi-reportdata, reference-data) |
| `docs/reference-example.md` | ✅ Done | BYOFS-1.5 standalone Java reference — no CDI/JNDI; all four patterns in ~110 LoC |
| `docs/implementation-status.md` | ✅ Done | This file |

---

## UC2 implementation (v6 SAS approach — current)

The current implementation uses the v6 design: owner mints a read User Delegation SAS and sends the URL; receiver performs a server-side copy via `BlobCopyFromUrlOptions` + `copyFromUrlWithResponse`. Bytes never transit the application server. BYOFS-1.3 metadata set atomically on destination.

| Artefact | Status | Notes |
|---|---|---|
| `FileIngestor` (file-store-core) | ✅ Done | `copyFromUrlWithResponse` with `BlobCopyFromUrlOptions.setMetadata(...)` |
| `IngestFileCommandHandler` (command-handler) | ✅ Done | `@Handles("notificationnotify.command.ingest-file")` → delegates to `FileIngestor` |
| RAML endpoint | ✅ Done | `POST /files/{fileId}` in `notificationnotify-command-api.raml` |
| JSON schema + example | ✅ Done | `notificationnotify.command.ingest-file.json` — `fileId`, `sourceUri`, `correlationId`, `filename` |
| DRL access control rule | ✅ Done | `command-action-ingest-file.drl` — `System Users` group |
| User Delegation SAS (owner side) | ✅ Done | `LiveReportBlobClientService.generateUserDelegationSas()` in `cpp-context-mi-reportdata` |
| `cpp-context-sjp` UC2 receiver | ✅ Done | `sjp-file-store` module (commit `3fa077ebed`, `byo-file-store` branch): `FileStorer`, `FileRetriever`, `FileIngestor`, `StoragePath`, `AzureBlobConfiguration`, `AzureBlobContainerClientProducer`, full unit + IT suites, and `sjp.ingest-file` command endpoint. |
| `cpp-context-system-doc-generator` UC2 owner | ✅ Done | `byo-file-store` branch. `RenderDocumentDelegate` stores SJP PDFs to `published/sjp-docs/` via `FileStorer`. `SjpDocumentPublisher` generates read-SAS URI from `blobFileId` — PostgreSQL round-trip eliminated. 11 tests added (`RenderDocumentDelegateTest` rewrite + new `SjpDocumentPublisherTest`). Full `runIntegrationTests.sh` passed: 78 tests, 0 failures, 0 skipped (2026-05-17). |
| Full receiver/owner list | ⚠️ Pending | Other UC2 services pending TA team confirmation. |

## BYOFS-2 epic — UC2: peer-to-peer `beginCopy` (v7, RBAC-only)

**Note:** The current UC2 implementation uses v6 SAS. The v7 BYOFS-2 design replaces SAS with RBAC-only cross-container access: owner sends a Service Bus message (no SAS); receiver SP pre-granted `Storage Blob Data Reader` on owner's container via IaC; receiver calls `blobClient.beginCopy(sourceBlobUri)`.

| Ticket | Title | Points | Status | Notes |
|---|---|---|---|---|
| BYOFS-2.1 | Bicep IaC: cross-container RBAC grant | 3 | ❌ Not started | TA/infra team. `Storage Blob Data Reader` on owner's container for each receiver identity. |
| BYOFS-2.2 | Service Bus message contract spec | 2 | ✅ Done | `docs/rbac-peer-copy-message-contract.md` — event name, payload fields (`fileId`, `blobUri`, `correlationId`, `filename`), owner/receiver responsibilities, v6 vs v7 comparison. |
| BYOFS-2.3 | Metadata carry-forward convention | 2 | ✅ Done | `docs/rbac-peer-copy-metadata.md` — why `beginCopy` does not auto-carry source metadata; `BlobBeginCopyOptions.setMetadata(...)` required on destination. |
| BYOFS-2.4 | Reference example: receiver-side `beginCopy` | 5 | ✅ Done | `FileIngestor.ingest()` in `notificationnotify-file-store-core` updated to `beginCopy(BlobBeginCopyOptions).waitForCompletion()`; unit tests updated in `FileIngestorTest`. |
| BYOFS-2.5 | Integration test (cross-container peer copy) | 5 | ✅ Done | `FileIngestorIT` — 4 tests against Azurite using canonical URLs (no SAS); asserts blob existence, `correlation_id`, `filename`, content round-trip. |
| BYOFS-2.6 | Adopter onboarding guide + SRE runbook | 3 | ✅ Done | `docs/rbac-peer-copy-adopter-guide.md` — owner/receiver implementation steps, JNDI config, IT guidance, SRE runbook with RBAC diagnostic commands. |
| BYOFS-2.7 | Pilot integration | 3 | ❌ Not started | Pending BYOFS-2.1 RBAC grant. Candidate: `system-doc-generator → sjp` SJP document transfer. |

---

## BYOFS-3 epic — UC2.1: read-and-process, no receiver container (v7)

| Ticket | Title | Points | Status |
|---|---|---|---|
| BYOFS-3.1 | Service Bus message contract spec for UC2.1 | 2 | ✅ Done | `docs/stream-to-sink-event-contract.md` — event name, payload fields, blobUri vs downloadableLink, skip behaviour, dual-mode auth. |
| BYOFS-3.2 | No-persistence stream-through convention spec | 2 | ✅ Done | `docs/streaming.md` Pattern 3 — cross-container stream-to-sink, dual-mode BlobClient, sink-buffering caveat. Summary table updated. |
| BYOFS-3.3 | Reference example: receiver-side stream-to-sink | 5 | ✅ Done | `docs/reference-example.md` `streamCrossContainerToBytes` method + pattern notes; `docs/byofs-use-cases.md` UC2.1 section. |
| BYOFS-3.4 | Integration test: cross-container stream-to-email | 5 | ⚠️ In progress | `LiveReportEmailDeliveryIT` written (4 tests). IT script run pending. |
| BYOFS-3.5 | Adopter onboarding guide + SRE runbook for UC2.1 | 3 | ✅ Done | `docs/stream-to-sink-adopter-guide.md` — subscription config, handler pattern, RBAC requirements, SRE runbook with SQL/CLI diagnostics. |
| BYOFS-3.6 | Pilot integration: `mi-reportdata → notification-notify` | 3 | ⚠️ In progress | Production code and unit tests committed and pushed. IT pending (`runIntegrationTests.sh`). BYOFS-2.1 RBAC grant required for production. |

---

## Cross-context migration status

See [context-migration-status.md](context-migration-status.md) for the current state of all 31 CPP contexts, including which use cases each context implements and what remains.

---

## What this module proves

- Azure Blob SDK integrates cleanly into WildFly 26 / CDI 2.0 with the `@Dependent`
  producer workaround for `final` class proxying (WELD-001410)
- JDK HTTP transport (`azure-core-http-jdk-httpclient`) avoids the Netty classloading
  conflict with WildFly 26's bundled Netty modules
- JNDI-injected config works for both connection string (Azurite) and credential-based
  (production) auth via `DefaultAzureCredential`
- The `internal/` / `published/<topic>/` / `inbox/<topic>/` path-prefix convention
  correctly isolates blob namespaces within a single container
- Azurite 3.33.0 is a viable local development and CI target
- `BlobStoreTestHelper` provides upload/download/exists/delete for IT test seeding
