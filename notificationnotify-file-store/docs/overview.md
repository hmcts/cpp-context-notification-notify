# BYO FileStore — Overview

A CDI module providing Azure Blob Storage configuration, path-prefix conventions, and standard file operations for the BYO (Bring Your Own) FileStore pattern on the Criminal Practice Platform. Targets WildFly 26.1.3 / Java 17.

Design documents live in [`hmcts/pe_arch_design_docs/mbd_filestore`](https://github.com/hmcts/pe_arch_design_docs/tree/master/mbd_filestore).

---

## Reference implementation

> **This module is the canonical reference for BYO FileStore migrations across the CPP platform.**

`notificationnotify-file-store` is the production-ready migration that other contexts should follow. If you are migrating a `cpp-context-*` service from `cp-file-service` to the direct Azure Blob SDK:

1. Read [`azure-blobstore-migration.md`](azure-blobstore-migration.md) — the complete step-by-step guide
2. Copy this module structure into your context, renaming `notificationnotify` → `{yourcontext}`
3. Follow the JNDI and POM patterns exactly as implemented here

---

## docs/ index

| Document | Summary |
|---|---|
| [`overview.md`](overview.md) | This file — module overview, use cases, design principles, module structure |
| [`azure-blobstore-migration.md`](azure-blobstore-migration.md) | Step-by-step guide for migrating a `cpp-context-*` WildFly service from `cp-file-service` to direct Azure Blob SDK |
| [`byofs-use-cases.md`](byofs-use-cases.md) | UC1/UC2/UC3 definitions with per-service implementation status |
| [`context-migration-status.md`](context-migration-status.md) | Cross-context migration tracker — all 31 CPP contexts with file storage usage |
| [`implementation-status.md`](implementation-status.md) | Detailed status against the v6 BYOFS-1 ticket plan; CDI wiring; test infra |
| [`non-standard-changes.md`](non-standard-changes.md) | Non-standard changes made in contexts we don't own (SJP, SDG, mi-reportdata, reference-data) |
| [`authentication.md`](authentication.md) | Dual-mode auth; Pattern 1 (single container) and Pattern 2 (CDI qualifiers) |
| [`jndi.md`](jndi.md) | Reference for every JNDI key with per-environment values and `standalone.xml` examples |
| [`metadata-convention.md`](metadata-convention.md) | Required blob metadata keys (`correlation_id`, `filename`) — normative definition |
| [`correlation-id.md`](correlation-id.md) | Per-caller `correlation_id` values, identity-as-path and name-based UUID patterns |
| [`streaming.md`](streaming.md) | How to stream blob content without buffering — three patterns plus what not to use |
| [`stream-to-sink-event-contract.md`](stream-to-sink-event-contract.md) | UC2.1 event contract — `public.mireportdata.live-report-generated` field spec, auth modes, skip behaviour |
| [`stream-to-sink-adopter-guide.md`](stream-to-sink-adopter-guide.md) | How to implement a UC2.1 receiver: subscription, handler, cross-container client, RBAC, SRE runbook |
| [`workload-identity-guide.md`](workload-identity-guide.md) | AKS Workload Identity wiring — FIC, ServiceAccount annotation, RBAC assignment |
| [`reference-example.md`](reference-example.md) | Standalone Java class — UC1/UC2/UC2.1 patterns, no CDI, no JNDI |
| [`design-decisions.md`](design-decisions.md) | Key architectural decisions and how they align with the v6 design |
| [`byofs-implementation-gaps.md`](byofs-implementation-gaps.md) | Platform-level gap tracker (Bicep IaC, lifecycle policy, SRE runbook) |

---

## Design principles

**1. CDI wrappers for standard operations; direct SDK for custom patterns**

`notificationnotify-file-store-core` provides `FileStorer`, `FileRetriever`, and `FileIngestor` — thin CDI wrappers around the three most common operations (upload, download, server-side copy). Use these for the standard UC1/UC2 patterns. Call `BlobContainerClient` directly only for custom operations such as streaming HTTP responses or piped processing downloads.

**2. Path-prefix convention is standardised via `StoragePath`**

`StoragePath` is the single place that constructs valid blob names. Choose the factory method that matches the use case:
- `StoragePath.internal()` — UC1: private files accessed only by the owning service (`internal/{fileId}`)
- `StoragePath.published(topic)` — UC2 owner: files shared with downstream consumers (`published/{topic}/{fileId}`)
- `StoragePath.inbox(topic)` — UC3 doc-gen callback target (`inbox/{topic}/{fileId}`)

---

## What a BYO FileStore is

> One Azure Blob Storage container per platform service. Each service owns its own slice of Azure Storage. There is no shared store.

The legacy `cp-file-service` is a single shared PostgreSQL BLOB store. BYO FileStore replaces that with a container-per-service model, where each service authenticates via **Workload Identity** (Entra ID Federated Identity Credential — no client secrets in production).

---

## Use cases

| ID | Name | Pattern | Status |
|---|---|---|---|
| **UC1** | Self-contained | Service stores to its own container; same service later reads. No peer involvement. | ✅ Done (BYOFS-1 v6) |
| **UC2** | Peer-to-peer transfer | Owner uploads to its own container; mints a read User Delegation SAS; sends SAS URL to receiver; receiver calls `copyFromUrlWithResponse` — server-side copy, bytes never traverse a pod. (v7 BYOFS-2 will replace SAS with RBAC-only `beginCopy`.) | ✅ Done (BYOFS-1 v6); v7 not started |
| **UC3** | Stream without persistent copy | Service streams a blob directly as an HTTP response, or receives a generated document via a doc-gen callback, without materialising it in heap. | ✅ Streaming done; doc-gen callback ❌ not started |

For notification-notify specifically:
- `PocaEmailsTask` uploads POCA email attachments → **UC1**
- `NotificationNotifyPublicEventProcessor.pocaEmailAlreadyReceived` deletes a blob → **UC1**
- `IngestFileCommandHandler` → `FileIngestor` server-side copies from a SAS URI → **UC2 receiver**

---

## Zero-trust controls (summary)

| Control | Mechanism |
|---|---|
| No static credentials | Workload Identity (FIC) — pod identity, no secrets |
| Least privilege | RBAC role assignments scoped to container or path prefix |
| Short-lived tokens | Entra access token TTL ≈1h |
| Audit | Azure Storage Diagnostic Logs → Log Analytics (`StorageBlobLogs`) |
| Threat detection | Microsoft Defender for Storage |

---

## Module structure

```
notificationnotify-file-store/
├── notificationnotify-file-store-core/       # AzureBlobContainerClientProducer, AzureBlobConfiguration,
│                                             # StoragePath, FileStorer, FileRetriever, FileIngestor
├── notificationnotify-file-store-bom/        # BOM for consuming modules
├── notificationnotify-file-store-test-utils/ # BlobStoreTestHelper for Azurite ITs
└── docs/                                     # This directory
```

---

## Authentication

`AzureBlobContainerClientProducer` resolves credentials in order:

1. **`azure.storage.connection-string` JNDI value** — used if non-blank. For local development against Azurite only.
2. **`DefaultAzureCredential`** — used otherwise. On AKS this resolves to Workload Identity automatically.

See [`jndi.md`](jndi.md) for JNDI key names and per-environment values, and [`authentication.md`](authentication.md) for CDI wiring patterns.

---

## Typical usage

```java
@Inject
private FileStorer fileStorer;

@Inject
private FileRetriever fileRetriever;

// UC1 upload
final UUID fileId = fileStorer.store(StoragePath.internal(), correlationId, "my-document.pdf", bytes);

// UC1 download
final Optional<byte[]> content = fileRetriever.retrieve(StoragePath.internal(), fileId);

// UC3 streaming — use BlobContainerClient directly
final BlobClient blobClient = blobContainerClient.getBlobClient(StoragePath.internal().blobName(fileId));
final StreamingOutput stream = output ->
        blobClient.downloadStreamWithResponse(output, new BlobRange(0, 1_000_000_000L),
                null, null, false, null, null);
return status(OK).entity(stream).build();
```

See [`azure-blobstore-migration.md`](azure-blobstore-migration.md) for the complete guide.

---

## Dependency management

`azure-storage-blob`, `azure-identity`, and `azure-core-http-jdk-httpclient` are declared in `notificationnotify-file-store/pom.xml`. `azure-core-http-jdk-httpclient` is used instead of `azure-core-http-netty` to avoid WildFly Netty classpath conflicts.

**TODO:** Move Azure SDK version declarations to `cpp-platform-maven-common-bom` once the pattern is approved platform-wide.

---

## Coordinates

```xml
<dependency>
    <groupId>uk.gov.moj.cpp.notification.notify</groupId>
    <artifactId>notificationnotify-file-store-core</artifactId>
</dependency>

<dependency>
    <groupId>uk.gov.moj.cpp.notification.notify</groupId>
    <artifactId>notificationnotify-file-store-test-utils</artifactId>
    <scope>test</scope>
</dependency>
```

---

## WELD-001409 — one core dependency per WAR

Only one module per WAR may depend on `notificationnotify-file-store-core`. Multiple transitives in the same WAR cause Weld to find two `@Produces BlobContainerClient` methods and throw `DeploymentException`. See [`azure-blobstore-migration.md`](azure-blobstore-migration.md) for full details.

---

## Local development

Use the `byo-file-store` branch of `cpp-developers-docker` — it adds the Azurite container and JNDI bindings to `standalone.xml`.

```bash
cd cpp-developers-docker
git checkout byo-file-store
```

---

## Build

```bash
# Build file-store modules only
mvn clean install -pl notificationnotify-file-store/notificationnotify-file-store-core,notificationnotify-file-store/notificationnotify-file-store-test-utils -am

# Full context build
mvn clean install
```

---

## BYOFS-1 ticket status

Full status in [`implementation-status.md`](implementation-status.md).

| Ticket | Title | Status |
|---|---|---|
| BYOFS-1.1 | Bicep IaC: per-service container + RBAC | ❌ Not started |
| BYOFS-1.2 | Bicep IaC: Lifecycle Management policy | ❌ Not started |
| BYOFS-1.3 | Metadata convention: `correlation_id` + `filename` | ✅ Done |
| BYOFS-1.4 | Workload Identity wiring guide | ✅ Done |
| BYOFS-1.5 | Reference example: direct SDK usage (≤200 LoC) | ✅ Done — [`reference-example.md`](reference-example.md) |
| BYOFS-1.6 | Integration test against Azurite | ✅ Done |
| BYOFS-1.7 | Onboarding guide + SRE runbook | 🟡 Partial — onboarding done; SRE runbook not started |
