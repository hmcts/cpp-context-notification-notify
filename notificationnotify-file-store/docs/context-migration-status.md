# BYO FileStore — Cross-Context Migration Status

Tracks which CPP contexts need to migrate to BYO FileStore (direct Azure Blob SDK), which use
cases they will implement, and their current status.

**Design reference:** `pe_arch_design_docs/mbd_filestore/byo-filestore-azure-native-design_v6.md`
**Canonical reference implementation:** `cpp-context-notification-notify` (`byo-file-store` branch)
**Last updated:** 2026-05-17

---

## Use case summary

| UC | Description |
|---|---|
| **UC1** | Service uploads its own files to its own container |
| **UC2 owner** | Service generates a short-lived read User Delegation SAS and sends the URI to a receiver |
| **UC2 receiver** | Service receives a SAS URI and performs a server-side copy into its own container |
| **UC3** | Service streams or reads a blob without storing a persistent copy (HTTP streaming or doc-gen callback) |

See [byofs-use-cases.md](byofs-use-cases.md) for the full use-case spec.

---

## Migrated — done

| Context | Use cases | Notes |
|---|---|---|
| `cpp-context-notification-notify` | UC1, UC2 receiver | Canonical reference. `FileStorer` (UC1 `PocaEmailsTask`), `FileIngestor` (UC2 `IngestFileCommandHandler`), `BlobContainerClient` direct (UC2 `AttachmentsRetriever`). `file-service-persistence` removed from `notificationnotify-event-processor`. |
| `cpp-context-mi-reportdata` | UC1, UC2 owner | Two containers (`@MislExtract`, `@LiveReport`). Old `AzureStorageAccountService` removed. User Delegation SAS generation in `LiveReportBlobClientService`. Full `runIntegrationTests.sh` passed: 314 tests, 0 failures, 11 skipped (2026-05-17). |
| `cpp-context-reference-data` | UC1, UC2 owner | Single container. New `publish-reference-data-file` command/endpoint/DRL; `ReferenceDataFilePublishedEventProcessor` generates public event with blob URI; judiciary processors migrated from `FileRetriever` to piped `BlobContainerClient` streams (`byo-file-store` branch). Full `runIntegrationTests.sh` passed: 862 tests, 0 failures, 4 skipped (2026-05-17). |
| `cpp-context-sjp` | UC1, UC2 receiver, UC3 | **UC1 + UC2 receiver + UC3 complete** (`byo-file-store` branch): `SjpServiceFileInterceptor` → `FileStorer`; query API resources → `BlobContainerClient` + `StreamingOutput` (no heap materialisation); `TransparencyReportRequestedProcessor` + `PressTransparencyReportRequestedProcessor` → `FileStorer.store(published("sdg-payloads"), ...)` + `SasUriGenerator.generateReadUri()`; `PressAndTransparencyReportStrategy` triggers `sjp.ingest-file` (UC2 receiver); `NoActionController.ingestFile()` + controller RAML + COMMAND_CONTROLLER DRL complete. Full `runIntegrationTests.sh` passed: 253 tests, 0 failures, 11 skipped (2026-05-17). |

---

## Partially migrated

These contexts have begun the BYO FileStore migration but have not completed all use cases. The
table covers two scenarios: contexts that have adopted the standard CDI pattern for some use cases
but still carry old file-service dependencies for others; and contexts that have gone direct to the
Azure Blob SDK without yet adopting the standard `FileStorer`/`FileRetriever`/`FileIngestor` CDI
pattern.

| Context | Use cases | What's done | What's missing |
|---|---|---|---|
| `cpp-context-system-doc-generator` | UC1, UC2 owner, UC3 | **UC1 + UC2 owner complete** (`byo-file-store` branch): UC1 — `PayloadRetrievalService` reads uploaded payload from `internal/` via `FileRetriever` and re-stores template payload via `FileStorer`; `RenderDocumentDelegate.fetchPayload()` reads template payload from `internal/` via `FileRetriever`. UC2 owner — `RenderDocumentDelegate` stores SJP transparency/press-report PDFs directly to Azure Blob under `published/sjp-docs/` via `FileStorer`. `SjpDocumentPublisher` generates a read-SAS URI — no PostgreSQL round-trip. `public.systemdocgenerator.events.document-available` carries `blobFileId`+`sourceUri` for SJP to consume. 11 tests added. End-to-end with SJP confirmed working via `TransparencyReportIT` + `PressTransparencyReportIT` (2026-05-17). Full `runIntegrationTests.sh` passed: 78 tests, 0 failures, 0 skipped (2026-05-17). | UC3 (write-SAS Event Grid callback) not yet started. |
| `cpp-context-listing-courtscheduler` | UC1, UC2 receiver | `AzureBlobClientService` calls `BlobServiceClient`/`BlobClient` directly including `copyFromUrl`. `DefaultAzureCredentialBuilder` in place. | Adopt `FileStorer`/`FileIngestor` CDI pattern; remove `rest-adapter-file-service` dependency; add BYOFS-1.3 metadata convention; add Azurite IT tests. |
| `cpp-context-referencedata-offences` | UC1 | `AzureBlobClientService` calls `BlobClient.upload()` and download directly. `DefaultAzureCredentialBuilder` in place. | Adopt `FileStorer`/`FileRetriever` CDI pattern; add BYOFS-1.3 metadata convention; add Azurite IT tests. |

---

## Not yet started — high impact

These contexts have the most extensive file storage usage and should be prioritised.

| Context | Use cases | Current pattern | Key classes | Notes |
|---|---|---|---|---|
| `cpp-context-material` | UC1, UC2 owner, UC3 | `file-service-api`, `file-service-persistence`, custom `AzureBlobClientService` | `AlfrescoUploadBundleTask`, `MaterialAlfrescoUploadTask`, `AzureBlobClientService` | ~231 references. Also integrates with Alfresco upload pipeline; UC2 owner SAS generation already exists and will need standardising. |
| `cpp-context-progression` | UC1, UC3 | `file-service-persistence`, `rest-adapter-file-service` | `UploadCourtDocumentHandler`, document generation services | ~205 references. |
| `cpp-context-resulting` | UC1, UC3 | `file-service-persistence` | `employerattachmenttoearnings`, `intentiontodisqualifynotice`, `resultorder` services | ~137 references. |
| `cpp-context-correspondence` | UC1, UC3 | `file-service-persistence`, `rest-adapter-file-service` | Multiple command and event handlers; `fileServiceId` on `CorrespondenceLogEntry` | ~110 references. Viewstore stores fileServiceId — migration requires data model decisions. |
| `cpp-context-prosecution-documentqueue` | UC3 | `file-service-persistence` | `FileServiceRetrievalService`, `DocumentContentService`, `RetrievalServiceProvider` | ~108 references. Read-only; depends on source service migrating first to know the blob URI. |

---

## Not yet started — moderate impact

| Context | Use cases | Current pattern | Notes |
|---|---|---|---|
| `cpp-context-prosecution-casefile` | UC1, UC3 | `file-service-persistence` | ~79 references. |
| `cpp-context-results` | UC1 | `file-service-persistence`, `rest-adapter-file-service` | ~80 references. |
| `cpp-context-staging-prosecutors` | UC1, UC3 | `file-service-persistence`, `rest-adapter-file-service` | ~78 references. Unbundling and file service pipeline. |
| `cpp-context-hearing-nows` | UC1, UC3 | `file-service-persistence` | ~71 references. `StorePayloadToFileServiceTask`, `MaterialUploadFileTask`, `FileUtil`. |
| `cpp-context-staging-dvla` | UC1, UC3 | `file-service-persistence` | ~63 references. |
| `cpp-context-defence` | UC1 | `file-service-persistence` | ~6 references. `DocumentGeneratorService.store()`. Low complexity. |
| `cpp-context-archiving` | UC1 | `file-service-persistence`, `rest-adapter-file-service` | Uploads audit payloads. |

---

## Not yet started — low impact / dependency only

These contexts declare file-service dependencies but have low active code usage. Confirm
whether the dependency is actually exercised before scheduling migration work.

| Context | Use cases | Current pattern | Notes |
|---|---|---|---|
| `cpp-context-staging-pnld-offences` | UC3 | `file-service-persistence`, `rest-adapter-file-service` | Read-only retrieval. |
| `cpp-context-staging-enforcement` | UC3 | Old file service pattern | Read-only. |
| `cpp-context-staging-prosecutors-spi` | Unknown | `file-service-persistence`, `rest-adapter-file-service` | Confirm active usage before scheduling. |
| `cpp-context-staging-dcs` | Unknown | `file-service-persistence` | Confirm active usage before scheduling. |
| `cpp-context-applications-courtorders` | Unknown | `rest-adapter-file-service` | Confirm active usage before scheduling. |
| `cpp-context-hearing` | Unknown | `file-service-persistence` | Confirm active usage before scheduling. |
| `cpp-context-listing` | Unknown | `file-service-api` | Confirm active usage before scheduling. |
| `cpp-context-staging` | Unknown | `file-service-persistence` | Confirm active usage before scheduling. |
| `cpp-context-subscriptions` | Unknown | `file-service-persistence` | Confirm active usage before scheduling. |
| `cpp-context-system-announcement` | Unknown | `rest-adapter-file-service` | Confirm active usage before scheduling. |

---

## Azure-native / out of scope

These contexts use the Azure Blob SDK or Azure Functions directly for their own purposes and
are not part of the file-service migration scope.

| Context | Notes |
|---|---|
| `cpp-context-staging-bulkscan` | Azure Functions-based ingest; direct SDK usage without file-service dependency. |
| `cpp-context-staging-dlrm` | Azure Functions-based; direct SDK usage. |

---

## Migration summary

| Status | Count |
|---|---|
| Fully migrated | 4 |
| Partially migrated | 3 |
| Not started — high impact | 5 |
| Not started — moderate impact | 7 |
| Not started — low impact / needs confirmation | 10 |
| Azure-native / out of scope | 2 |
| **Total with file storage usage** | **31** |
