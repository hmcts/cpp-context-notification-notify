# BYO FileStore — Non-Standard Changes per Context

Changes made to CPP contexts during the BYO FileStore migration that go beyond the standard
`{context}-file-store` module addition. These are modifications to existing production files —
RAMLs, Drools rules, event schemas, event-processor strategies, query resources, IT test
helpers — that were required to wire up the blob flow end-to-end.

Teams who own these contexts should review and accept these changes before merging.

**Standard upgrade artefacts (not listed here):** new `{context}-file-store-core` module,
`AzureBlobConfiguration`, `AzureBlobContainerClientProducer`, `StoragePath`, `FileStorer`,
`FileRetriever`, `FileIngestor`, CDI `beans.xml`, module POMs, test-utils module,
integration tests against Azurite, JNDI entries in `standalone.xml`, POM version bumps.

---

## Contents

1. [cpp-context-sjp](#cpp-context-sjp)
2. [cpp-context-system-doc-generator](#cpp-context-system-doc-generator)
3. [cpp-context-mi-reportdata](#cpp-context-mi-reportdata)
4. [cpp-context-reference-data](#cpp-context-reference-data)
5. [cpp-context-notification-notify](#cpp-context-notification-notify)
6. [Notes](#notes)

---
---

# cpp-context-sjp:

**Branch:** `byo-file-store`  **Repo owner:** SJP team

---

### Command API — new `sjp.ingest-file` endpoint (UC2 receiver)

A new HTTP command endpoint was added to receive ingest-file requests from the SDG event
processor (UC2 receiver pattern).

| File | Change |
|---|---|
| `sjp-command/sjp-command-api/src/raml/sjp-command-api.raml` | New `POST /files/{fileId}` endpoint; action `sjp.ingest-file` |
| `sjp-command/sjp-command-api/src/raml/json/schema/sjp.ingest-file.json` | New schema: `fileId`, `correlationId`, `filename`, `sourceUri` |
| `sjp-command/sjp-command-api/src/raml/json/sjp.ingest-file.json` | New example payload |
| `sjp-command/sjp-command-api/src/main/java/…/IngestFileApi.java` | New JAX-RS resource interface for the endpoint |
| `sjp-command/sjp-command-api/src/main/resources/rules/command-action-ingest-file.drl` | New COMMAND_API Drools rule: allows `sjp.ingest-file` for `System Users` group |
| `sjp-command/sjp-command-api/src/main/java/…/accesscontrol/RuleConstants.java` | Added `getIngestFileActionGroups()` returning `["System Users"]` |

### Command API — `SjpServiceFileInterceptor` replaced

| File | Change |
|---|---|
| `sjp-command/sjp-command-api/src/main/java/…/interceptor/SjpServiceFileInterceptor.java` | Replaced: old PostgreSQL file-service upload removed; now delegates to `FileStorer` |

### Command Controller — JMS routing for `sjp.command.ingest-file`

The CPP framework generates a JMS bridge class from the controller messaging RAML.
Without an entry here the bridge has no `@Handles` method for `sjp.command.ingest-file`
and the COMMAND_API throws `MissingHandlerException` at runtime.

| File | Change |
|---|---|
| `sjp-command/sjp-command-controller/src/raml/sjp-command-controller.messaging.raml` | Added `application/vnd.sjp.command.ingest-file+json: !!null` |
| `sjp-command/sjp-command-controller/src/main/resources/rules/command-ingest-file-controller.drl` | **New file.** COMMAND_CONTROLLER Drools rule allowing `sjp.command.ingest-file` to pass the controller-level access control check |

### Command Controller — `NoActionController` pass-through for `sjp.command.ingest-file`

The CPP framework handler registry at the COMMAND_CONTROLLER tier is populated by
`@Handles`-annotated methods on CDI beans, not by the generated JMS bridge class. Without
an entry here the controller dispatcher throws `MissingHandlerException` at runtime.

| File | Change |
|---|---|
| `sjp-command/sjp-command-controller/src/main/java/…/NoActionController.java` | Added `ingestFile()` method annotated `@Handles("sjp.command.ingest-file")` — pass-through via `sender.send(envelope)` |

### Command Handler — `IngestFileCommandHandler`

| File | Change |
|---|---|
| `sjp-command/sjp-command-handler/src/main/java/…/IngestFileCommandHandler.java` | New handler: `@Handles("sjp.command.ingest-file")` → delegates to `FileIngestor` to server-side copy a blob from source SAS URI into SJP's `internal/` container |
| `sjp-command/sjp-command-handler/src/raml/sjp-command-handler.messaging.raml` | Added `application/vnd.sjp.command.ingest-file+json` |
| `sjp-command/sjp-command-handler/src/raml/json/schema/sjp.command.ingest-file.json` | New handler-level schema |
| `sjp-command/sjp-command-handler/src/raml/json/sjp.command.ingest-file.json` | New handler-level example |

### Event Processor — `PressAndTransparencyReportStrategy` (blob flow)

| File | Change |
|---|---|
| `sjp-event/sjp-event-processor/src/main/java/…/PressAndTransparencyReportStrategy.java` | Added `dispatchIngestFile()`: dispatches `sjp.ingest-file` when `sourceUri` is present in the `document-available` payload. Added `getDocumentMetadata()`: downloads PDF bytes via the SAS URI to extract page-count and file-size metadata |

### Event Processor — `TransparencyReportRequestedProcessor` / `PressTransparencyReportRequestedProcessor`

| File | Change |
|---|---|
| `sjp-event/sjp-event-processor/src/main/java/…/TransparencyReportRequestedProcessor.java` | Replaced PostgreSQL file-service upload with `FileStorer.store(published("sdg-payloads"), …)` + `SasUriGenerator.generateReadUri()` |
| `sjp-event/sjp-event-processor/src/main/java/…/PressTransparencyReportRequestedProcessor.java` | Same for press transparency reports |

### Event Processor — `document-available` JSON schema

| File | Change |
|---|---|
| `sjp-event/sjp-event-processor/src/yaml/json/schema/public.systemdocgenerator.events.document-available.json` | Added `blobFileId` and `sourceUri` fields. Removed `payloadFileServiceId` and `documentFileServiceId` from `required` array (absent in the new blob flow from SDG) |

### Query API — streaming blob download (UC3)

| File | Change |
|---|---|
| `sjp-query/sjp-query-api/src/main/java/…/DefaultQueryApiTransparencyReportContentFileIdResource.java` | Replaced PostgreSQL file-service download with `BlobContainerClient` + JAX-RS `StreamingOutput`; no heap materialisation |
| `sjp-query/sjp-query-api/src/main/java/…/DefaultQueryApiPressTransparencyReportContentFileIdResource.java` | Same for press transparency report content |
| `sjp-query/sjp-query-api/src/main/java/…/ResourceUtility.java` | Shared streaming utility extracted for both resources |

### Event Listener — `blobFileId` stored in viewstore

| File | Change |
|---|---|
| `sjp-event/sjp-event-listener/src/main/java/…/TransparencyReportListener.java` | Updated to store `blobFileId` (from `document-available`) in viewstore instead of PostgreSQL `fileServiceId` |
| `sjp-event/sjp-event-listener/src/main/java/…/PressTransparencyReportListener.java` | Same for press transparency report |

### Viewstore — entity and repository changes

| File | Change |
|---|---|
| `sjp-viewstore/sjp-viewstore-persistence/src/main/java/…/TransparencyReportMetadata.java` | `blobFileId` field added; legacy `fileServiceId` removed |
| `sjp-viewstore/sjp-viewstore-persistence/src/main/java/…/PressTransparencyReportMetadata.java` | Same |
| `sjp-viewstore/sjp-viewstore-persistence/src/main/java/…/TransparencyReportMetadataRepository.java` | Query methods updated for `blobFileId` |
| `sjp-viewstore/sjp-viewstore-persistence/src/main/java/…/PressTransparencyReportMetadataRepository.java` | Same |

### Integration tests — reworked to use blob flow

The IT tests previously used `FileServiceDBHelper.createStubFile()` (a PostgreSQL stub).
They have been reworked to upload a real PDF to Azurite and generate a SAS URI instead.

| File | Change |
|---|---|
| `sjp-integration-test/src/test/java/…/TransparencyReportIT.java` | `shouldGenerateTransparencyPDFReports`: replaced `createStubFile` with `BlobStoreTestHelper.forLocalAzurite` upload + `generateDockerAccessibleSasUri`; passes blob-payload `SysDocGeneratorHelper` overload |
| `sjp-integration-test/src/test/java/…/PressTransparencyReportIT.java` | `shouldGeneratePressTransparencyPDFReports`: same rework |
| `sjp-integration-test/src/test/java/…/SysDocGeneratorHelper.java` | New overload `publishDocumentAvailablePublicEvent(UUID, String, UUID, String)` that publishes a `document-available` event via `JsonEnvelope` with `DEFAULT_USER_ID` in metadata, so the `System Users` access control check on `sjp.ingest-file` passes in the IT environment |

---
---

# cpp-context-system-doc-generator

**Branch:** `byo-file-store`  **Repo owner:** SDG team

---

### Command API — `PayloadRetrievalService` (UC1)

The command API previously retrieved the uploaded payload via `cp-file-service` (`FileService`).
It now reads directly from Azure Blob via `FileRetriever` and re-stores the extracted template
payload via `FileStorer`.

| File | Change |
|---|---|
| `systemdocgenerator-command/systemdocgenerator-command-api/pom.xml` | Added `systemdocgenerator-file-store-core` dependency |
| `systemdocgenerator-command/…/service/PayloadRetrievalService.java` | Replaced `FileService` with `FileRetriever` + `FileStorer`; reads raw payload from `internal/` via `FileRetriever.retrieve()`; extracts `templateName`, `conversionFormat`, `templatePayload`; re-stores template payload via `FileStorer.store(internal(), correlationId, templateName + ".json", bytes)` |
| `systemdocgenerator-command/…/command/api/SystemDocGeneratorCommandApi.java` | Removed `throws FileServiceException` from `uploadJsonPayload()` |

### Event Processor — `RenderDocumentDelegate` (UC1 fetch + UC2 owner store)

SDG renders PDFs and now both fetches the payload and stores the rendered output directly
via Azure Blob instead of the PostgreSQL file-service.

| File | Change |
|---|---|
| `systemdocgenerator-event/…/job/render/RenderDocumentDelegate.java` | Added `FileRetriever` injection; `fetchPayload()` rewritten to read from `internal/` via `FileRetriever.retrieve()` instead of `FileService`; added `FileStorer` injection for SJP PDF storage; `TEMPLATE_TO_FILENAME` map added for SJP template names |
| `systemdocgenerator-event/…/job/render/RenderDocumentState.java` | Added `blobFileId` field to carry the Azure blob UUID through job state |

### Event Processor — `SjpDocumentPublisher` (UC2 owner — SAS generation)

| File | Change |
|---|---|
| `systemdocgenerator-event/…/SjpDocumentPublisher.java` | **New class.** Reads `blobFileId` from job state; generates a short-lived read SAS URI; augments the `document-available` event payload with `blobFileId` + `sourceUri`. No PostgreSQL round-trip. |

### Event Processor — `DocumentEventProcessor` (wires `SjpDocumentPublisher`)

| File | Change |
|---|---|
| `systemdocgenerator-event/…/DocumentEventProcessor.java` | Injects `SjpDocumentPublisher`; extracts `payloadSourceUri` from `additionalInformation` list; calls `sjpDocumentPublisher.augmentForSjp(payload)` before announcing `document-available` |

---
---

# cpp-context-mi-reportdata

**Branch:** `byo-file-store`  **Repo owner:** MI Reportdata team

---

### Command Handler — `url` field made optional

| File | Change |
|---|---|
| `mireportdata-command/mireportdata-command-handler/src/main/java/…/LiveReportCommandHandler.java` | `url` field in `LiveReportGenerationOutcomeRecorded` event builder guarded with null check; no longer unconditionally required |

### JSON Schemas — `url` removed from `required`

| File | Change |
|---|---|
| `mireportdata-command/mireportdata-command-handler/src/raml/json/schema/mireportdata.command.live-report-generation-record-outcome.json` | Removed `"url"` from `required` array |
| `mireportdata-event/mireportdata-event-listener/src/yaml/json/schema/mireportdata.event.live-report-generation-outcome-recorded.json` | Same: `"url"` removed from `required` array |

### Event Processor — Azure SDK migration

The two classes below previously used the legacy `CloudBlockBlob` SDK. They have been
migrated to `azure-storage-blob` 12.x.

| File | Change |
|---|---|
| `mireportdata-event/mireportdata-event-processor/src/main/java/…/LiveReportBlobClientService.java` | Replaced legacy `CloudBlockBlob` with Azure SDK 12.x; SAS generation now uses `UserDelegationKey`; blob uploads carry `correlationId` in blob metadata |
| `mireportdata-event/mireportdata-event-processor/src/main/java/…/LiveReportGenerationProvider.java` | Replaced `var` with explicit types; `correlationId` threaded through to blob upload call; SAS URI expiry reduced from 120 min to 15 min |

### Query API — correlation ID propagation

| File | Change |
|---|---|
| `mireportdata-query/mireportdata-query-api/src/main/java/…/DefaultQueryApiCrimeDataResource.java` | Generates and passes correlation ID to blob upload |
| `mireportdata-query/mireportdata-query-api/src/main/java/…/DefaultQueryApiGenerateMiExtractResource.java` | Passes file UUID as correlation ID to blob upload |

---
---

# cpp-context-reference-data

**Branch:** `byo-file-store`  **Repo owner:** Reference Data team

---

### Command API — new `referencedata.publish-reference-data-file` endpoint

| File | Change |
|---|---|
| `referencedata-command/referencedata-command-api/src/raml/referencedata_command_api.raml` | New `POST /upload-file` endpoint; action `referencedata.publish-reference-data-file` |
| `referencedata-command/referencedata-command-api/src/raml/json/schema/referencedata.publish-reference-data-file.schema.json` | New schema |
| `referencedata-command/referencedata-command-api/src/raml/json/referencedata.publish-reference-data-file.json` | New example payload |
| `referencedata-command/referencedata-command-api/src/main/resources/…/accesscontrol/reference-data-file.drl` | New COMMAND_API Drools rule authorising `referencedata.publish-reference-data-file` for `System Users` |
| `referencedata-command/referencedata-command-api/src/main/java/…/accesscontrol/RuleConstants.java` | Added `getPublishReferenceDataFileGroups()` returning `["System Users"]` |

### Command API — `FileMetadataRetriever` removed; new interceptor

| File | Change |
|---|---|
| `referencedata-command/referencedata-command-api/src/main/java/…/JudiciaryCommandApi.java` | Removed `FileMetadataRetriever` injection |
| `referencedata-command/referencedata-command-api/src/main/java/…/FileMetadataRetriever.java` | **Deleted.** No longer needed; metadata retrieved directly from blob properties |
| `referencedata-command/referencedata-command-api/src/main/java/…/interceptor/ReferenceDataFileInterceptor.java` | **New class.** Handles multipart uploads, stores to Azure Blob, injects `fileId` and `correlationId` into command payload |

### Command Handler — `PublishReferenceDataFileCommandHandler`

| File | Change |
|---|---|
| `referencedata-command/referencedata-command-handler/src/main/java/…/PublishReferenceDataFileCommandHandler.java` | **New handler.** `@Handles("referencedata.publish-reference-data-file")` → raises `ReferenceDataFilePublished` domain event |

### Domain Event — `ReferenceDataFilePublished`

| File | Change |
|---|---|
| `referencedata-domain/referencedata-domain-event/src/main/java/…/ReferenceDataFilePublished.java` | **New event class.** Fields: `id`, `recipientEmail`, `subject`, `filename`, `correlationId` |

### Event Processor — blob-based file delivery

The judiciary processors previously used `FileRetriever`. They now stream directly from
`BlobContainerClient`.

| File | Change |
|---|---|
| `referencedata-event/referencedata-event-processor/src/main/java/…/ReferenceDataFilePublishedEventProcessor.java` | **New processor.** Publishes a public event containing blob URI and `correlationId` |
| `referencedata-event/referencedata-event-processor/src/main/java/…/JudiciaryCpUserIdEventProcessor.java` | Replaced `FileRetriever` with `BlobContainerClient`; uses piped streams + `ManagedExecutorService` for large CSV streaming; filename read from blob metadata; `FileServiceException` → `IOException` |
| `referencedata-event/referencedata-event-processor/src/main/java/…/JudiciaryUploadedEventProcessor.java` | Same refactoring as `JudiciaryCpUserIdEventProcessor` |
| `referencedata-event/referencedata-event-processor/src/main/java/…/utils/ElinksJudiciaryDataComparator.java` | Updated to use blob storage instead of file service |
| `referencedata-event/referencedata-event-processor/src/main/java/…/utils/PssCommonService.java` | Updated to use blob storage instead of file service |

---
---

# cpp-context-notification-notify

**Branch:** `byo-file-store`  **Repo owner:** Notification Notify team

---

### Command API — new `notificationnotify.ingest-file` endpoint (UC2 receiver)

| File | Change |
|---|---|
| `notificationnotify-command/notificationnotify-command-api/src/raml/notificationnotify-command-api.raml` | New `POST /files/{fileId}` endpoint; action `notificationnotify.ingest-file` |
| `notificationnotify-command/notificationnotify-command-api/src/raml/json/schema/notificationnotify.ingest-file.json` | New schema |
| `notificationnotify-command/notificationnotify-command-api/src/raml/json/notificationnotify.ingest-file.json` | New example payload |
| `notificationnotify-command/notificationnotify-command-api/src/main/java/…/NotifyCommandApi.java` | Added `ingestFile()` handler method for the new endpoint |
| `notificationnotify-command/notificationnotify-command-api/src/main/resources/…/accesscontrol/command-action-ingest-file.drl` | New COMMAND_API Drools rule authorising `notificationnotify.ingest-file` for `System Users` |
| `notificationnotify-command/notificationnotify-command-api/src/main/java/…/accesscontrol/RuleConstants.java` | Added `getIngestFileActionGroups()` returning `["System Users"]` |

### Command Handler — `IngestFileCommandHandler`

| File | Change |
|---|---|
| `notificationnotify-command/notificationnotify-command-handler/src/main/java/…/IngestFileCommandHandler.java` | **New handler.** `@Handles("notificationnotify.command.ingest-file")` → delegates to `FileIngestor` |
| `notificationnotify-command/notificationnotify-command-handler/src/raml/notificationnotify-command-handler.messaging.raml` | Added `application/vnd.notificationnotify.command.ingest-file+json` |
| `notificationnotify-command/notificationnotify-command-handler/src/raml/json/schema/notificationnotify.command.ingest-file.json` | New handler-level schema |
| `notificationnotify-command/notificationnotify-command-handler/src/raml/json/notificationnotify.command.ingest-file.json` | New handler-level example |

### Event Processor — blob-based attachment handling and new subscription

| File | Change |
|---|---|
| `notificationnotify-event/notificationnotify-event-processor/src/main/java/…/NotificationNotifyPublicEventProcessor.java` | Added `referenceDataFilePublished()` handler to consume `public.referencedata.event.reference-data-file-published` and send as email; `pocaEmailAlreadyReceived` handler now deletes from blob storage |
| `notificationnotify-event/notificationnotify-event-processor/src/main/java/…/sender/AttachmentsRetriever.java` | Replaced `FileRetriever` with `BlobContainerClient`; filename read from blob metadata; range-based download for large files; `FileServiceException` → `BlobStorageException` |
| `notificationnotify-event/notificationnotify-event-processor/src/main/java/…/sender/BlobFileEmailSender.java` | **New class.** Streams blobs directly to SMTP without in-memory buffering |
| `notificationnotify-event/notificationnotify-event-processor/src/main/java/…/task/pocaemail/PocaEmailsTask.java` | Replaced file-service SDK upload with `FileStorer`; `XmlProcessingException` → `DocumentUploadException`; retry logic updated for blob upload |
| `notificationnotify-event/notificationnotify-event-processor/src/yaml/subscriptions-descriptor.yaml` | Added subscription to `public.referencedata.event.reference-data-file-published` |

---
---

## Notes

- The `sjp-command-controller.messaging.raml` and `command-ingest-file-controller.drl` entries
  are pure plumbing required by the CPP framework's generated JMS bridge and controller-level
  access control. There is no business logic added to the controller — the command passes
  straight through to the handler.
- The `document-available` schema change (removing `payloadFileServiceId` /
  `documentFileServiceId` from `required`) is a **schema contract change** — any other consumer
  that validates against this schema must be checked for impact before merging.
- `SysDocGeneratorHelper` (IT test code only): the `DEFAULT_USER_ID` in the envelope ensures
  the `System Users` DRL check passes in the test environment. In production the user context
  is carried from the original upstream request through the event metadata chain.
