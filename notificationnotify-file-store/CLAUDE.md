# notificationnotify-file-store — Claude Code context

## Purpose

This is the **reference implementation** of the CPP BYO (Bring Your Own) FileStore pattern.
It is the first production-ready migration away from `cp-file-service`/Alfresco to direct Azure Blob SDK.
Other `cpp-context-*` services should use this module and `docs/azure-blobstore-migration.md` as their template.

The SJP context (`cpp-context-sjp/file-store`) holds the original spike. This module supersedes it as the
canonical reference — it has proper CPP Maven ancestry, correct package names, and all lessons learned baked
into the implementation and docs.
           

---

## Module layout

```
notificationnotify-file-store/
├── notificationnotify-file-store-bom/        pom packaging — version management for core + test-utils
├── notificationnotify-file-store-core/       CDI producer, JNDI config, StoragePath factory
├── notificationnotify-file-store-test-utils/ BlobStoreTestHelper for IT tests
└── docs/                                     Migration guides — azure-blobstore-migration.md is the primary doc
```

**Package root**: `uk.gov.moj.cpp.notification.notify.filestore.azure`

**Maven coordinates**: `uk.gov.moj.cpp.notification.notify` group, version inherited from root POM via
`${project.version}`.

---

## Callers in notificationnotify-event-processor

Two call sites use `BlobContainerClient` directly (UC1 — self-contained):

| Class | Operation | Path prefix |
|---|---|---|
| `PocaEmailsTask.uploadSingleDocument()` | Upload POCA attachment on arrival | `internal/` |
| `NotificationNotifyPublicEventProcessor.pocaEmailAlreadyReceived()` | Delete blob when duplicate email received | `internal/` |

One call site intentionally left on `cp-file-service` (UC3 — deferred):

| Class | Reason |
|---|---|
| `AttachmentsRetriever` | Reads files stored by progression/results/resulting. Cannot migrate until those contexts also migrate to BYO. |

---

## Critical technical constraints

These will burn you if you forget them. All are documented with solutions in `docs/azure-blobstore-migration.md`.

### `BlobContainerClient` is `final` — CDI cannot proxy it

Weld cannot subclass final types. The producer must be `@Dependent` (not `@ApplicationScoped`).
If you see `WELD-001410` or `WeldException: Cannot proxy a final type`, check the producer scope.

### `BlobContainerClient` is `final` — Mockito cannot mock it without `mock-maker-inline`

Unit tests that `@Mock BlobContainerClient` or `@Mock BlobClient` will fail unless
`src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` contains exactly `mock-maker-inline`.
This file must be present in every module whose tests mock Azure classes.

### `downloadContent()`, `openInputStream()`, `downloadStream()` all NPE inside WildFly

Use `downloadStreamWithResponse(outputStream, new BlobRange(0, 1_000_000_000L), ...)` instead.
The oversized range is intentional — it forces a 206 Partial Content response from Azurite,
which avoids an NPE in the JDK HTTP transport. See `docs/STREAMING.md`.

### WELD-001409 — one `file-store-core` dependency per WAR

Only one module per WAR deployment may pull in `notificationnotify-file-store-core`. Multiple modules
in the same WAR means duplicate `@Produces BlobContainerClient` → Weld deployment failure.
`notificationnotify-event-processor.war` and `notificationnotify-service.war` are separate WARs, so
each can declare the dependency independently.

### Netty must be excluded everywhere

Every Azure SDK dependency that transitively pulls `azure-core-http-netty` must exclude it explicitly.
WildFly 26 bundles its own Netty — two versions on the classpath causes classloading failures that are
very hard to diagnose. Use `azure-core-http-jdk-httpclient` instead (JDK 11+ built-in HTTP client).

### `block-secrets.sh` hook false-positives on Azurite credentials

The pre-commit hook matches the Azurite well-known public dev key against "Azure Storage key" patterns.
This key is intentionally public — it is not a real secret. If the hook blocks a write, bypass with:
```bash
CPP_HOOKS_DISABLE=1 python3 - <<'PYEOF'
# write file content here
PYEOF
```

### JNDI entries must be in `standalone.xml` for all three keys

`AzureBlobContainerClientProducer` reads three JNDI values via `@Value`. There are no defaults in code.
Missing entries cause a `NamingException` at WAR deploy time, not at startup.

```xml
<!-- notificationnotify-event-processor — add under its <bindings> subsystem entry -->
<lookup name="java:/app/notificationnotify-event-processor/azure.storage.connection-string"
        lookup="java:global/cpp.azure.storage.connection-string"/>
<lookup name="java:/app/notificationnotify-event-processor/azure.storage.endpoint"
        lookup="java:global/cpp.azure.storage.endpoint"/>
<simple name="java:/app/notificationnotify-event-processor/azure.storage.container-name"
        value="notificationnotify-files" type="java.lang.String"/>
```

See `docs/JNDI.md` for the full reference.

---

## Running a migration with Claude Code

This section documents how to use Claude Code to migrate a `cpp-context-*` service from
`cp-file-service` to the BYO FileStore pattern, based on how this module was built.

### Recommended session setup

```bash
claude --permission-mode auto
```

Using `--permission-mode auto` avoids permission prompts for standard tools (grep, curl, docker exec).
Only genuinely destructive actions (git reset --hard, rm -rf) should require confirmation.

### Giving Claude the right context

At the start of a migration session, tell Claude:

1. **Which context you're migrating** — e.g. `cpp-context-progression`
2. **Which call sites need changing** — identify classes that use `FileStorer`, `FileRetriever`, or `FileServiceException`
3. **Which use case each call site represents** — UC1 (self-contained), UC2 (peer-to-peer), or UC3 (doc-gen)
4. **The notificationnotify module as the reference** — point Claude at this module and `docs/azure-blobstore-migration.md`

Example prompt to start:
```
I'm migrating cpp-context-progression from cp-file-service to BYO FileStore.
Use notificationnotify-file-store as the reference implementation and
docs/azure-blobstore-migration.md as the migration guide.

Call sites to migrate:
- ProgressionDocumentInterceptor.java — UC1 upload
- DocumentReadyEventProcessor.java — UC1 read
- CrossContextDocumentRetriever.java — UC3, leave on cp-file-service for now

Please start by creating the progression-file-store module structure.
```

### What Claude will need to read

Claude will need access to:
- `notificationnotify-file-store/docs/azure-blobstore-migration.md` — the primary guide
- `notificationnotify-file-store/notificationnotify-file-store-core/src/main/java/` — to copy the CDI producer and StoragePath
- `notificationnotify-file-store/notificationnotify-file-store-test-utils/` — to copy the test helper
- The target context's existing POM hierarchy to understand the module structure
- `cpp-developers-docker/containers/wildfly/config/standalone.xml` — to add JNDI entries

### What to verify after each migration

1. **Compile check**: `mvn compile -pl {context}-event/{context}-event-processor -am`
2. **Unit tests**: `mvn test -pl {context}-event/{context}-event-processor`
3. **Build the service WAR**: `mvn clean install -pl {context}-service -am`
4. **Deploy and smoke test** against local Azurite: `./runIntegrationTests.sh`

### Known traps Claude may fall into

| Trap | Symptom | Fix |
|---|---|---|
| Missing `mock-maker-inline` | `Cannot mock/spy class BlobContainerClient` in tests | Add `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` with content `mock-maker-inline` |
| `@ApplicationScoped` on producer | `WELD-001410` at deploy | Change to `@Dependent` |
| BOM import cycle | `The dependencies of type=pom and with scope=import form a cycle` | Do not import the child BOM at root POM level; manage versions directly in `<dependencyManagement>` using `${project.version}` |
| `any()` in verify() | Weak assertions that miss bugs | Use `ArgumentCaptor` to capture blob names, then assert `startsWith("internal/")` and extract UUID for consistency checks |
| `downloadContent()` in event processor | `NullPointerException` inside WildFly | Use `downloadStreamWithResponse` with `BlobRange(0, 1_000_000_000L)` |
| Wrong service-parent-pom version | Dependency resolution failures | Check other contexts on the same branch to confirm which `17.104.x` release to use |
| Forgetting `notificationnotify-file-store-core` in service POM | Azure SDK classes missing from service WAR at runtime | Both event-processor WAR and service WAR need the dependency — the service bundles event-processor *classes* via `classifier=classes` but not its transitive deps |

---

## Memory hints for Claude

When working in this module across multiple sessions, Claude should check its saved memories for:

- `feedback_no_download_to_memory.md` — Azure Blob download approach (use `downloadStreamWithResponse`)
- `feedback_immutability.md` — all variables must be `final`
- `feedback_no_any_in_tests.md` — no `any()` in verify(); use specific values or ArgumentCaptor
- `feedback_test_naming.md` — test methods start with `should` in camelCase
- `feedback_dependency_versions.md` — never hardcode versions; use root POM dependencyManagement
- `project_bom_migration.md` — BOM import strategy and what's not in common-bom
