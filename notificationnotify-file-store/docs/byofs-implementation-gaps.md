# BYO FileStore — Implementation Gap Tracker

**Design ref:** [byo-filestore-azure-native-design_v6.md](byo-filestore-azure-native-design_v6.md) · [byo_jira_tickets_only_v6.md](byo_jira_tickets_only_v6.md)
**UC scope for this programme phase:** UC1 (self-contained), UC2 (peer-to-peer SAS transfer), UC3 (doc-gen callback).
**Last updated:** 2026-05-15

---

## Platform-level deliverables (BYOFS-1.x) — architecture / platform team

These are not service code. Each is a platform-team artefact. Status tracked here for completeness.

| Ticket | Title | Status |
|--------|-------|--------|
| BYOFS-1.1 | Bicep module — per-service Azure Blob container + RBAC | ❌ Not started |
| BYOFS-1.2 | Bicep module — Azure Storage Lifecycle Management policy (TTL) | ❌ Not started |
| BYOFS-1.3 | Metadata convention spec — `correlation_id` + `filename` on every blob | ✅ Done — `docs/metadata-convention.md` |
| BYOFS-1.4 | Workload Identity wiring guide for adopting services | ✅ Done — `docs/workload-identity-guide.md` |
| BYOFS-1.5 | Reference example — direct Azure SDK usage with metadata + lifecycle | ✅ Done — `docs/reference-example.md` |
| BYOFS-1.6 | Integration test against Azurite | ✅ Done — `FileStorerIT`, `FileRetrieverIT`, `FileIngestorIT` in `notificationnotify-file-store-core` |
| BYOFS-1.7 | Adopter onboarding guide + SRE runbook | 🟡 Partial — onboarding done (`azure-blobstore-migration.md`); production SRE runbook not started |

---

### BYOFS-1.1 — Bicep IaC requirements (TA team)

Owner: TA / infra team. Suggested location: `cpp-aks-deploy`.

The Bicep module must provision, per adopting service:

- **One Blob container** in the shared CPP storage account, named `{context}-{env}` (e.g. `sjp-dev`).
- **`Storage Blob Data Contributor`** RBAC assignment: service's managed identity on its own container. Required for UC1 upload/download.
- **`Storage Blob Delegator`** RBAC assignment: service's managed identity on the **storage account** (not the container). Required for User Delegation SAS minting (UC2 owners). This role can only be assigned at account scope.
- **`Storage Blob Data Reader`** RBAC assignments: service's managed identity on any peer containers it reads from (UC2 receivers and UC3 stream-readers).

Parameterised by: storage account resource ID, container name, service managed identity principal ID, list of peer containers needing reader access.

Depends on: Workload Identity pod identity already wired per `docs/workload-identity-guide.md`.

---

### BYOFS-1.2 — Lifecycle Management policy requirements (TA team)

Owner: TA / infra team. Depends on BYOFS-1.1 (containers must exist before policy rules reference them).

Azure lifecycle policies are account-scoped (`Microsoft.Storage/storageAccounts/managementPolicies`) but can filter by blob name prefix. Required rules:

| Prefix | Suggested TTL | Rationale |
|---|---|---|
| `internal/` | 30 days | Service's own working files; retrieval is short-lived |
| `published/{topic}/` | 90 days | Shared files; receiver may ingest up to days after publication |
| `inbox/{topic}/` | 14 days | Already copied to receiver; source copy no longer needed |

TTL values are design decisions that need TA confirmation — the values above are a starting proposal only.
Policy must use `tierToArchive` or `delete` actions on `blockBlob` base blobs filtered by `prefixMatch`.

---

### BYOFS-1.7 — SRE runbook requirements (SRE input needed)

Owner: SRE team to fill in thresholds and escalation. We can write the skeleton; they must approve and complete it.

The production runbook (to be added to `docs/azure-blobstore-migration.md` or a separate `docs/sre-runbook.md`) must cover:

- **Azure Monitor alert thresholds** — upload error rate, download latency p99, auth failure rate (`BlobAuthorizationError`). Thresholds to be set by SRE.
- **On-call diagnostic steps** — how to identify whether a blob failure is auth (Workload Identity token expired, RBAC misconfigured), storage (account throttling, container missing), or application (bad blob name, wrong container JNDI value).
- **Credential rotation procedure** — connection strings are test-only; production uses Workload Identity. Rotation means cycling the AKS pod identity / federated credential, not a password change. Steps to be confirmed with the AKS platform team.
- **Escalation contacts** — Azure support path, internal storage account owner, AKS platform team contact.

---

## cpp-context-notification-notify

### FileStorer wired into PocaEmailsTask ✅
`PocaEmailsTask` previously duplicated the blob upload logic inline. Now delegates to `FileStorer.store()`.

### UC2 receiver implemented ✅
`FileIngestor` + `IngestFileCommandHandler` implement the UC2 receiver pattern (design doc §18.2). Server-side copy via `BlobCopyFromUrlOptions` + `copyFromUrlWithResponse`. `PocaEmailsTask` attachment uploads remain UC1.

### Integration tests ✅
`FileStorerIT` (5 tests), `FileRetrieverIT` (4 tests), and `FileIngestorIT` (4 tests) pass against Azurite.  
Run with: `mvn verify -pl notificationnotify-file-store/notificationnotify-file-store-core -Pintegration-test`

---

## cpp-context-reference-data

All non-atomic upload patterns fixed. ✅

---

## cpp-context-mi-reportdata

All SAS generation updated to User Delegation SAS. ✅

---

## UC2 — peer-to-peer SAS transfer

UC2 covers the pattern where a file stored in one service's container is shared with another service via a short-lived SAS URL, without copying the bytes.

### SAS minting — owner side ⚠️
**Pattern:** Owner service generates a read-SAS on an existing blob and sends the URL to the receiver via a command/event.  
`cpp-context-mi-reportdata` already updated to `generateUserDelegationSas`. ✅  
**Remaining:** Confirm full list of UC2 owner services with TA team; apply the same fix to any others.

### Receiver-side file ingestion — `POST /files/{fileId}` ✅
**Pattern:** Receiver service accepts a SAS URI and performs a server-side blob copy into its own container via `BlobCopyFromUrlOptions` + `copyFromUrlWithResponse`.  
**Implemented for `cpp-context-notification-notify`:** `FileIngestor` (file-store-core), `IngestFileCommandHandler`, RAML endpoint `/files/{fileId}`, JSON schema/example, DRL rule (`System Users` group).  
**Remaining:** Full receiver list to be confirmed with TA team; apply same pattern to any others.

### Legacy `cp-file-service` migration ❌
All services still calling `cp-file-service` for file exchange must be migrated to the UC2 SAS pattern. Affected contexts include `cpp-context-progression`, `cpp-context-resulting`, `cpp-context-correspondence`, `cpp-context-hearing-nows`, and others — full list to be confirmed with TA team.

---

## UC3 — doc-gen callback (Event Grid + write-SAS for SDG)

### Event Grid subscription for doc-gen result ❌
**Pattern:** SDG (doc-gen service) writes a generated document to a blob container, then raises an Event Grid event. The receiving service subscribes to that event to pick up the file via a write-SAS.  
**Required:** Event Grid subscription wiring, SAS validation, and ingestion into the receiving service's own container.  
**Affected services:** To be confirmed with TA team.

### Write-SAS provisioning for SDG ❌
**Pattern:** Before triggering doc-gen, the requesting service mints a write-SAS on a pre-agreed blob path and passes it to SDG so it can upload directly.  
**Required:** Write-SAS generation using User Delegation SAS (`generateUserDelegationSas` with write permission).  
**Affected services:** To be confirmed with TA team.

---

## Definition of code complete (per service)

A service is code complete for BYOFS when:

- [ ] CDI producer initialises correctly with both auth modes (connection string / DefaultAzureCredential)
- [ ] Every blob upload uses `uploadWithResponse(BlobParallelUploadOptions.setMetadata(...), null, NONE)` — no separate `setMetadata()` calls
- [ ] Every class that was touched by this migration has a full junit/mockito test with 100% coverage, excepting only Exceptions and Pojos
- [ ] All blobstore usages in the production code is covered by at least one Integration Test
- [ ] **`runIntegrationTests.sh` passes in full** — this is the gate. Unit tests alone do not constitute code complete.
