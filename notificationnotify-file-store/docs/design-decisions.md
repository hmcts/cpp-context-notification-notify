# BYO FileStore — Design Decisions

This document captures the key architectural decisions made during the spike and
subsequent notification-notify migration, and how they align with the v6 design.

**Last updated:** 2026-05-15
**Primary design refs:** `pe_arch_design_docs/mbd_filestore/byo-filestore-azure-native-design_v6.md`,
`ensuring-zerotrust-azure-native_v6.md`, `byo_jira_tickets_only_uc2_v7.md` (BYOFS-2),
`byo_jira_tickets_only_uc2.1_v7.md` (BYOFS-3)

---

## Decisions made by v6

### 1. Spring Boot wrapper library — retracted

**Was open in v5:** Build a Spring Boot starter library, or a WildFly CDI library?

**v6 decision:** Neither.  v6 retracts the wrapper library for UC1 entirely.  The
owning service calls the Azure Blob SDK directly with `DefaultAzureCredential`.
The platform team ships IaC + a metadata convention + a copy-reference example —
no library JAR.

---

### 2. REST surface — not needed for UC1

**v6 decision:** No REST surface for UC1.  The owning service uses the SDK
directly.  No Spring controller, no OpenAPI spec, no Bearer JWT filter chain.
UC2 and UC3 are deferred.

---

### 3. PostgreSQL `file_metadata` table — not needed for UC1

**v6 decision:** No PG table, no liquibase migrations, no scheduler.  Per-file
context is stored as two Azure Blob metadata keys directly on the blob:
`correlation_id` and `filename`.  TTL is enforced by an Azure Storage Lifecycle
Management policy on the container — a Bicep IaC module, not application code.

---

### 4. Cross-service access model

#### v6 implementation (current — SAS + `copyFromUrlWithResponse`)

**Implemented in this branch for `cpp-context-notification-notify`:**

1. Owner uploads to its own container and mints a short-lived read User Delegation SAS.
2. Owner sends the SAS URL to the receiver via a command/event.
3. Receiver calls `blobClient.copyFromUrlWithResponse(new BlobCopyFromUrlOptions(sasUri).setMetadata(...))` — server-side copy, bytes never traverse a pod.
4. Receiver sets `correlation_id` + `filename` atomically via `BlobCopyFromUrlOptions.setMetadata()`.

See `FileIngestor` in `notificationnotify-file-store-core` and `IngestFileCommandHandler` in `notificationnotify-command-handler`.

#### v7 design (BYOFS-2 — not yet started, replaces SAS with RBAC)

**BYOFS-2 decision (funded, not yet implemented):**

1. Owner uploads to its own container.
2. Owner sends a Service Bus message: `{sourceBlobUri, correlation_id}`.  No SAS.
3. Receiver SP is pre-granted `Storage Blob Data Reader` on owner's container via IaC.
4. Receiver calls `blobClient.beginCopy(sourceBlobUri)` — server-side copy, no SAS required.
5. Receiver carries `correlation_id` and `filename` forward on its own copy.
6. Owner's source expires via Lifecycle Management.

**UC2.1 (BYOFS-3 — not yet started):** receiver streams bytes into a sink with no container of its own. Service Bus message carries `{sourceBlobUri, correlation_id, processingContext}`.

When BYOFS-2 is implemented it will supersede the v6 SAS approach. Until then, the v6 SAS pattern is the active implementation.

---

## Still open — deferred with UC2/UC3

### Recovering the blob path at delete time — RESOLVED

In the v6 direct-SDK model callers call `blobClient.deleteIfExists()` with the full
blob name.  When migrating contexts that currently call `fileStorer.delete(uuid)`
with no path, they need a way to recover the prefix.

**Chosen approach: Derive it.**  If all files of a given type always use the same
prefix, construct the path at the call site without persisting it.

notification-notify resolution: `pocaEmailAlreadyReceived` uses
`StoragePath.internal().blobName(pocaFileId)`.  POCA attachments are always stored
under `internal/` by `PocaEmailsTask`, so the prefix is known at the delete call site
without any viewstore lookup.  See `NotificationNotifyPublicEventProcessor` for the
implementation.

### Cross-service file retrieval — UUID alone no longer locates a blob

**v7 resolution:** The Service Bus message carries the full `sourceBlobUri`, so the
receiver never needs to derive or look up the locator.

---

## Open implementation items (UC1, current scope)

| Item | What needs doing |
|---|---|
| **BYOFS-1.1 Bicep IaC** | Per-service container + RBAC module. Not started. |
| **BYOFS-1.2 Lifecycle Management** | Delete policy module (30-day TTL). Not started. |
| **BYOFS-1.5 Reference example** | ✅ Done — `docs/reference-example.md`. Standalone Java class (~110 LoC): connection-string + Workload Identity auth, UC1 upload/download, UC2 server-side copy, UC2 User Delegation SAS. No CDI, no JNDI. |
| **BYOFS-1.7 SRE runbook** | Onboarding guide (`azure-blobstore-migration.md`) is done. Production operations runbook (alert thresholds, on-call steps, connection string rotation) is not started. |

---

## Related documents

| Document | What it covers |
|---|---|
| [implementation-status.md](implementation-status.md) | Full status of artefacts vs v6 BYOFS-1 ticket plan |
| [azure-blobstore-migration.md](azure-blobstore-migration.md) | Step-by-step migration guide |
| [streaming.md](streaming.md) | Streaming patterns — Pattern 1 (HTTP response) and Pattern 2 (PipedInputStream) |
| [metadata-convention.md](metadata-convention.md) | `correlation_id` + `filename` key spec (BYOFS-1.3) |
| [correlation-id.md](correlation-id.md) | Per-caller `correlation_id` values and naming patterns |
| [jndi.md](jndi.md) | JNDI configuration reference |
| [workload-identity-guide.md](workload-identity-guide.md) | AKS Workload Identity wiring guide |
| [context-migration-status.md](context-migration-status.md) | Cross-context migration tracker — all 31 CPP contexts with file storage usage |
