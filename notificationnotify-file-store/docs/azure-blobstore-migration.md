# Migrating a CPP Context Service to Azure Blob Storage

This guide covers everything needed to migrate a `cpp-context-*` WildFly service from
`cp-file-service` / Alfresco to Azure Blob Storage (BYO FileStore pattern).

**`cpp-context-notification-notify` is the canonical reference implementation.**
All code, POMs, and docs here reflect the production-ready pattern adopted by the
team that owns notification-notify.  Other teams migrating their own context should
follow this guide using their own context's coordinates — do NOT take a Maven
dependency on `uk.gov.moj.cpp.notification.notify.filestore` artifacts.  Copy the
module structure instead (see §2).

The v6 design is **IaC + Direct SDK**: each service owns one container, calls the
Azure Blob SDK directly (no wrapper library), and authenticates via Workload Identity
on AKS.

---

## Table of Contents

1. [Concepts](#1-concepts)
2. [Maven module structure](#2-maven-module-structure)
3. [CDI producer](#3-cdi-producer)
4. [JNDI configuration (standalone.xml)](#4-jndi-configuration-standalonxml)
5. [Docker / Azurite local setup](#5-docker--azurite-local-setup)
6. [Healthcheck exclusions](#6-healthcheck-exclusions)
7. [Uploading a file (command interceptor)](#7-uploading-a-file-command-interceptor)
8. [Downloading a file (event processor)](#8-downloading-a-file-event-processor)
9. [Integration testing](#9-integration-testing)
10. [CDI ambiguity — one core dependency per service WAR](#10-cdi-ambiguity--one-core-dependency-per-service-war)
11. [Gotchas and known problems](#11-gotchas-and-known-problems)

---

## 1. Concepts

### One container per service

Every service gets exactly one Azure Blob Storage container.  No shared containers
across services.

### Path-prefix convention

Blobs are organised by path prefix inside the container:

| Prefix | Used for |
|---|---|
| `internal/` | Files the service stores and reads back itself (UC1) |
| `inbox/<topic>/` | Files written by an external system for this service to read (UC3 — doc-gen callback) |
| `published/<topic>/` | Files published for downstream consumers (UC2 — deferred) |

### Required blob metadata

Every blob must carry two flat metadata keys (BYOFS-1.3 convention):

| Key | Value |
|---|---|
| `correlation_id` | UUID linking the blob to its originating business entity |
| `filename` | The original human-readable filename |

---

## 2. Maven module structure

### Create a `{context}-file-store` multi-module under your context root

The notification-notify reference has:

```
notificationnotify-file-store/
├── pom.xml                                  (packaging=pom, parent=notificationnotify root)
├── notificationnotify-file-store-bom/       (BOM — manages -core and -test-utils versions)
├── notificationnotify-file-store-core/      (CDI producers + StoragePath — runtime code)
└── notificationnotify-file-store-test-utils/ (BlobStoreTestHelper — test scope only)
```

For your own context, substitute `{context}` throughout.

### Root context POM — version management

Add the `azure-core-http-jdk-httpclient` version property in `<properties>`, and the
entry in `<dependencyManagement>`.  The other Azure SDK versions come from `common-bom`
(already available via `service-parent-pom`).

```xml
<properties>
    <!-- TODO: move to common-bom once Azure SDK promoted out of spike -->
    <azure-core-http-jdk-httpclient.version>1.0.0-beta.14</azure-core-http-jdk-httpclient.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- azure-storage-blob and azure-identity versions come from common-bom -->
        <dependency>
            <groupId>com.azure</groupId>
            <artifactId>azure-core-http-jdk-httpclient</artifactId>
            <version>${azure-core-http-jdk-httpclient.version}</version>
        </dependency>

        <!-- manage your own file-store-core and file-store-test-utils here -->
        <dependency>
            <groupId>uk.gov.moj.cpp.{context}</groupId>
            <artifactId>{context}-file-store-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>uk.gov.moj.cpp.{context}</groupId>
            <artifactId>{context}-file-store-test-utils</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### `{context}-file-store-core` POM — runtime dependencies

```xml
<dependencies>
    <!-- Azure Blob SDK — Netty MUST be excluded on every Azure dependency -->
    <dependency>
        <groupId>com.azure</groupId>
        <artifactId>azure-storage-blob</artifactId>
        <exclusions>
            <exclusion>
                <groupId>com.azure</groupId>
                <artifactId>azure-core-http-netty</artifactId>
            </exclusion>
        </exclusions>
    </dependency>
    <!-- DefaultAzureCredential for production (Workload Identity on AKS) -->
    <dependency>
        <groupId>com.azure</groupId>
        <artifactId>azure-identity</artifactId>
        <exclusions>
            <exclusion>
                <groupId>com.azure</groupId>
                <artifactId>azure-core-http-netty</artifactId>
            </exclusion>
        </exclusions>
    </dependency>
    <!-- Replaces Netty as the HTTP transport inside WildFly -->
    <dependency>
        <groupId>com.azure</groupId>
        <artifactId>azure-core-http-jdk-httpclient</artifactId>
    </dependency>
    <!-- CPP framework utilities (Value annotation etc.) -->
    <dependency>
        <groupId>uk.gov.justice.services</groupId>
        <artifactId>utilities-core</artifactId>
    </dependency>
    <dependency>
        <groupId>javax</groupId>
        <artifactId>javaee-api</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

> **Gotcha — always exclude Netty on every Azure dependency.**  If you add
> `azure-identity` later and forget the exclusion, Netty gets pulled in transitively
> and WildFly will fail to deploy with classloading errors.  Verify:
>
> ```bash
> mvn dependency:tree -Dincludes="com.azure:azure-core-http-netty"
> ```
>
> The output should be empty for every module.

### `{context}-file-store-test-utils` POM — test infrastructure

```xml
<dependencies>
    <dependency>
        <groupId>com.azure</groupId>
        <artifactId>azure-storage-blob</artifactId>
        <exclusions>
            <exclusion>
                <groupId>com.azure</groupId>
                <artifactId>azure-core-http-netty</artifactId>
            </exclusion>
        </exclusions>
    </dependency>
    <dependency>
        <groupId>com.azure</groupId>
        <artifactId>azure-core-http-jdk-httpclient</artifactId>
    </dependency>
</dependencies>
```

### WAR modules that need blob access — add `{context}-file-store-core`

**Only add `{context}-file-store-core` to ONE WAR in the service** — see §10 for why.

```xml
<dependency>
    <groupId>uk.gov.moj.cpp.{context}</groupId>
    <artifactId>{context}-file-store-core</artifactId>
    <classifier>classes</classifier>
</dependency>
```

The `classifier=classes` is needed because WAR modules produce classes JARs as
secondary artifacts under the CPP build conventions.

---

## 3. CDI producer

`{context}-file-store-core` provides two CDI beans in an `azure/` package.

### `AzureBlobConfiguration.java`

```java
package uk.gov.moj.cpp.{context}.filestore.azure;

import uk.gov.justice.services.common.configuration.Value;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class AzureBlobConfiguration {

    @Inject
    @Value(key = "azure.storage.connection-string")
    private String connectionString;

    @Inject
    @Value(key = "azure.storage.endpoint")
    private String endpoint;

    @Inject
    @Value(key = "azure.storage.container-name")
    private String containerName;

    public String getConnectionString() { return connectionString; }
    public String getEndpoint()         { return endpoint; }
    public String getContainerName()    { return containerName; }
}
```

### `AzureBlobContainerClientProducer.java`

```java
package uk.gov.moj.cpp.{context}.filestore.azure;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class AzureBlobContainerClientProducer {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AzureBlobContainerClientProducer.class);

    @Inject
    private AzureBlobConfiguration azureBlobConfiguration;

    private BlobContainerClient blobContainerClient;

    @PostConstruct
    public void initialise() {
        final String connectionString = azureBlobConfiguration.getConnectionString();
        final BlobServiceClient blobServiceClient;
        if (connectionString != null && !connectionString.isBlank()) {
            blobServiceClient = new BlobServiceClientBuilder()
                    .connectionString(connectionString)
                    .buildClient();
        } else {
            blobServiceClient = new BlobServiceClientBuilder()
                    .credential(new DefaultAzureCredentialBuilder().build())
                    .endpoint(azureBlobConfiguration.getEndpoint())
                    .buildClient();
        }
        blobContainerClient =
                blobServiceClient.getBlobContainerClient(
                        azureBlobConfiguration.getContainerName());
        try {
            blobContainerClient.createIfNotExists();
        } catch (final RuntimeException e) {
            // Azurite returns XML for ContainerAlreadyExists; the JDK HTTP transport
            // cannot parse it as JSON. Treat as "already exists" and proceed.
            LOGGER.warn("createIfNotExists failed for container '{}' — assuming it " +
                            "already exists: {}",
                    azureBlobConfiguration.getContainerName(), e.getMessage());
        }
    }

    // BlobContainerClient is final — Weld cannot proxy it for @ApplicationScoped.
    // @Dependent injects the real shared instance constructed in initialise().
    @Produces
    @Dependent
    public BlobContainerClient blobContainerClient() {
        return blobContainerClient;
    }
}
```

> **Gotcha — `@Dependent` not `@ApplicationScoped` on the producer method.**
> `BlobContainerClient` is a `final` class.  Weld cannot create a proxy subclass for
> it, so annotating the `@Produces` method with `@ApplicationScoped` causes
> `WELD-001410` at deploy time.  Use `@Dependent` — the single shared instance is
> created once in `@PostConstruct` and returned on every injection point.

### `StoragePath.java`

```java
package uk.gov.moj.cpp.{context}.filestore.azure;

import java.util.UUID;

public class StoragePath {

    private final String prefix;

    private StoragePath(final String prefix) {
        this.prefix = prefix;
    }

    public static StoragePath internal() {
        return new StoragePath("internal");
    }

    public static StoragePath published(final String topic) {
        return new StoragePath("published/" + topic);
    }

    public static StoragePath inbox(final String topic) {
        return new StoragePath("inbox/" + topic);
    }

    public String blobName(final UUID fileId) {
        return prefix + "/" + fileId;
    }

    public String prefix() {
        return prefix;
    }
}
```

Inject `BlobContainerClient` wherever you need blob access:

```java
@Inject
private BlobContainerClient blobContainerClient;
```

---

## 4. JNDI configuration (standalone.xml)

Each WAR that calls blob storage needs three JNDI entries.  The naming
convention is `java:/app/{war-name}/{key}`.

### Azurite (local dev / integration tests)

Add under the `<bindings>` section in
`cpp-developers-docker/containers/wildfly/config/standalone.xml`.

The Azurite connection string uses the well-known Azurite dev account key (a public
test credential — see [Azurite docs](https://learn.microsoft.com/en-us/azure/storage/common/storage-use-azurite)):

```xml
<!-- notificationnotify-event-processor — source of truth for the three values -->
<simple name="java:/app/notificationnotify-event-processor/azure.storage.connection-string"
        value="DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://cpp-azurite:10000/devstoreaccount1;"
        type="java.lang.String"/>
<simple name="java:/app/notificationnotify-event-processor/azure.storage.endpoint"
        value="http://cpp-azurite:10000/devstoreaccount1"
        type="java.lang.String"/>
<simple name="java:/app/notificationnotify-event-processor/azure.storage.container-name"
        value="notificationnotify-files"
        type="java.lang.String"/>

<!-- notificationnotify-service delegates to event-processor values via JNDI lookup -->
<lookup name="java:/app/notificationnotify-service/azure.storage.connection-string"
        lookup="java:/app/notificationnotify-event-processor/azure.storage.connection-string"/>
<lookup name="java:/app/notificationnotify-service/azure.storage.endpoint"
        lookup="java:/app/notificationnotify-event-processor/azure.storage.endpoint"/>
<lookup name="java:/app/notificationnotify-service/azure.storage.container-name"
        lookup="java:/app/notificationnotify-event-processor/azure.storage.container-name"/>
```

> **Global shortcut pattern:** If multiple services share the same Azurite
> connection string and endpoint, declare them globally once and look up per-service:
>
> ```xml
> <simple name="java:global/azure.storage.connection-string"
>         value="DefaultEndpointsProtocol=http;..."/>
> <simple name="java:global/azure.storage.endpoint"
>         value="http://cpp-azurite:10000/devstoreaccount1"/>
>
> <!-- Per-service: only connection-string and endpoint look up global;
>      container-name is always service-specific -->
> <lookup name="java:/app/notificationnotify-event-processor/azure.storage.connection-string"
>         lookup="java:global/azure.storage.connection-string"/>
> ```

### Production (AKS)

Set `connection-string` to empty string `""` so the producer falls through to
`DefaultAzureCredential` + `endpoint`:

```xml
<simple name="java:/app/notificationnotify-event-processor/azure.storage.connection-string"
        value=""
        type="java.lang.String"/>
<simple name="java:/app/notificationnotify-event-processor/azure.storage.endpoint"
        value="https://{storageaccount}.blob.core.windows.net"
        type="java.lang.String"/>
<simple name="java:/app/notificationnotify-event-processor/azure.storage.container-name"
        value="notificationnotify-files-{env}"
        type="java.lang.String"/>
```

> **Gotcha — JNDI key resolution uses the deployed WAR name, not the Maven module
> name.**  `@Value(key = "azure.storage.connection-string")` resolves to
> `java:/app/{deployed-war-name}/azure.storage.connection-string` at runtime.
> Verify the actual WAR name with:
> ```bash
> docker exec containers-cpp-wildfly-1 \
>   /opt/jboss/wildfly/bin/jboss-cli.sh --connect --command="deployment-info"
> ```
> The WAR name in `standalone.xml` must match the deployed name exactly.

---

## 5. Docker / Azurite local setup

### `cpp-developers-docker` branch prerequisite

> **You must use the `byo-file-store` branch of `cpp-developers-docker` for local development and
> integration testing.** The `java-17` branch (the normal local-dev base) does not yet contain the
> Azurite container definition, the global JNDI entries, or the per-service `standalone.xml` bindings
> that BYO FileStore requires.
>
> Once all contexts have migrated, the `byo-file-store` branch will be merged into `java-17`.
> Until then, treat `byo-file-store` as the working branch for anyone using BYO FileStore locally.

```bash
cd cpp-developers-docker
git checkout byo-file-store
git pull
```

The branch is ahead of `java-17` by the following commits (in order of landing):

| Commit | What it adds |
|---|---|
| `Add SJP Azure Blob Storage config; pin Azurite 3.33.0; disable SJP pull mechanism` | Azurite service in `docker-compose.yml`; SJP JNDI entries; Azurite pinned to `3.33.0` |
| `Add Azure Blob Storage JNDI config for reference-data services` | reference-data JNDI entries |
| `Fix ARTEMIS_INSTANCE_ETC_URI compose warning by hardcoding path` | Unrelated compose fix, included on this branch |
| `Consolidate Azure Blob JNDI: global connection-string and endpoint, add referencedata-service lookups` | Introduces `java:global` shortcuts (see below); adds notificationnotify entries |

#### Global JNDI shortcuts

The branch introduces two global entries that all per-service entries look up rather than repeating the
Azurite connection string and endpoint in every binding:

```xml
<!-- Single source of truth for Azurite — all per-service connection-string entries look this up -->
<lookup name="java:global/cpp.azure.storage.connection-string"
        lookup="java:global/dcs.document.azure.storage.connection-string"/>

<!-- Azurite blob endpoint -->
<simple name="java:global/cpp.azure.storage.endpoint"
        value="http://cpp-azurite:10000/devstoreaccount1" type="java.lang.String"/>
```

#### Adding a new context to `standalone.xml`

Every new context that adopts BYO FileStore needs **two sets of entries** — one for the event-processor
WAR and one for the service WAR. The service WAR entry for `container-name` looks up the
event-processor's entry rather than repeating the value:

```xml
<!-- {context}-event-processor WAR -->
<lookup name="java:/app/{context}-event-processor/azure.storage.connection-string"
        lookup="java:global/cpp.azure.storage.connection-string"/>
<lookup name="java:/app/{context}-event-processor/azure.storage.endpoint"
        lookup="java:global/cpp.azure.storage.endpoint"/>
<simple name="java:/app/{context}-event-processor/azure.storage.container-name"
        value="{context}-files" type="java.lang.String"/>

<!-- {context}-service WAR — container-name looks up the event-processor entry -->
<lookup name="java:/app/{context}-service/azure.storage.connection-string"
        lookup="java:global/cpp.azure.storage.connection-string"/>
<lookup name="java:/app/{context}-service/azure.storage.endpoint"
        lookup="java:global/cpp.azure.storage.endpoint"/>
<lookup name="java:/app/{context}-service/azure.storage.container-name"
        lookup="java:/app/{context}-event-processor/azure.storage.container-name"/>
```

The notificationnotify entries (already on the branch) follow this exact pattern:

```xml
<lookup name="java:/app/notificationnotify-event-processor/azure.storage.connection-string"
        lookup="java:global/cpp.azure.storage.connection-string"/>
<lookup name="java:/app/notificationnotify-event-processor/azure.storage.endpoint"
        lookup="java:global/cpp.azure.storage.endpoint"/>
<simple name="java:/app/notificationnotify-event-processor/azure.storage.container-name"
        value="notificationnotify-files" type="java.lang.String"/>

<lookup name="java:/app/notificationnotify-service/azure.storage.connection-string"
        lookup="java:global/cpp.azure.storage.connection-string"/>
<lookup name="java:/app/notificationnotify-service/azure.storage.endpoint"
        lookup="java:global/cpp.azure.storage.endpoint"/>
<lookup name="java:/app/notificationnotify-service/azure.storage.container-name"
        lookup="java:/app/notificationnotify-event-processor/azure.storage.container-name"/>
```

See [`docs/jndi.md`](jndi.md) for the full per-environment reference including production AKS values.

---

### Azurite version

Pin Azurite to `3.33.0` in `docker-compose.yml`.  Later versions NPE with the JDK
HTTP transport on JDK 17.0.14+.

```yaml
azurite:
  image: mcr.microsoft.com/azure-storage/azurite:3.33.0
  ...
  command: "azurite --blobHost 0.0.0.0 --debug /data/debug.log"
```

The `--debug /data/debug.log` flag is invaluable when diagnosing SDK–Azurite
communication problems:

```bash
docker exec cpp-azurite cat /data/debug.log | tail -100
```

### docker-compose profile

Azurite runs under the `azurite` profile in `cpp-developers-docker`.  Start it
alongside Elasticsearch:

```bash
docker compose --profile es --profile azurite up -d
```

Or via `runIntegrationTests.sh`:

```bash
buildAndStartContainers "--profile es --profile azurite"
```

### runIntegrationTests.sh changes for BYO FileStore

**1. Replace `buildAndStartContainersWithElasticSearch` with the profile form**

```bash
# Before:
buildAndStartContainersWithElasticSearch

# After:
buildAndStartContainers "--profile es --profile azurite"
```

**2. Remove `runFileServiceLiquibase` from `runLiquibase()`**

The Azure BYO FileStore has no database table.  Remove:

```bash
# Remove this line:
runFileServiceLiquibase
```

Leaving it in will fail on a clean Postgres instance with no `cp-file-service` schema.

---

## 6. Healthcheck exclusions

The framework registers a `FileStoreHealthcheck` by default.  Once migrated away
from `cp-file-service`, this healthcheck always fails.  Exclude it alongside
`JobStoreHealthcheck` in the context's `IgnoredHealthcheckNamesProvider`.

Find the class that `@Specializes` `DefaultIgnoredHealthcheckNamesProvider` in the
`{context}-healthchecks` module and add `FILE_STORE_HEALTHCHECK_NAME`:

```java
import static java.util.List.of;
import static uk.gov.justice.services.healthcheck.healthchecks.FileStoreHealthcheck.FILE_STORE_HEALTHCHECK_NAME;
import static uk.gov.justice.services.healthcheck.healthchecks.JobStoreHealthcheck.JOB_STORE_HEALTHCHECK_NAME;

import uk.gov.justice.services.healthcheck.api.DefaultIgnoredHealthcheckNamesProvider;

import java.util.List;

import javax.enterprise.inject.Specializes;

@Specializes
public class NotificationNotifyIgnoredHealthcheckNamesProvider
        extends DefaultIgnoredHealthcheckNamesProvider {

    public NotificationNotifyIgnoredHealthcheckNamesProvider() {
        // Required by CDI.
    }

    @Override
    public List<String> getNamesOfIgnoredHealthChecks() {
        return of(JOB_STORE_HEALTHCHECK_NAME, FILE_STORE_HEALTHCHECK_NAME);
    }
}
```

Update the corresponding test to assert `size() == 2` and verify both entries.

> **Why this matters:** Missing exclusion → every `/healthcheck` returns `UNHEALTHY`
> → AKS readiness probe fails → pod never receives traffic.

---

## 7. Uploading a file (command interceptor)

The pattern handles multipart file uploads in an `Interceptor` that extracts the
file stream, uploads it to blob storage, and replaces the multipart field with the
resulting `fileId` UUID in the JSON payload.

```java
public class MyServiceFileInterceptor implements Interceptor {

    private static final StoragePath BLOB_PATH = StoragePath.internal();

    @Inject
    private DocumentTypeValidator documentTypeValidator;

    @Inject
    private BlobContainerClient blobContainerClient;

    @Override
    public InterceptorContext process(final InterceptorContext interceptorContext,
                                     final InterceptorChain interceptorChain) {
        final Optional<Object> inputParameterOptional =
                interceptorContext.getInputParameter("fileInputDetailsList");

        if (inputParameterOptional.isEmpty()) {
            return interceptorChain.processNext(interceptorContext);
        }

        final List<FileInputDetails> fileInputDetailsList =
                (List<FileInputDetails>) inputParameterOptional.get();

        for (final FileInputDetails fileInputDetails : fileInputDetailsList) {
            if (!documentTypeValidator.isValid(fileInputDetails.getFileName())) {
                throw new ForbiddenRequestException(
                        "Allowed only doc|docx|jpg|jpeg|pdf|txt extensions");
            }
        }

        final JsonEnvelope inputEnvelope = interceptorContext.inputEnvelope();
        final String correlationId = correlationIdFrom(inputEnvelope);
        final Map<String, UUID> results = new HashMap<>();

        for (final FileInputDetails fileInputDetails : fileInputDetailsList) {
            final UUID fileId = randomUUID();
            blobContainerClient
                    .getBlobClient(BLOB_PATH.blobName(fileId))
                    .uploadWithResponse(
                            new BlobParallelUploadOptions(fileInputDetails.getInputStream())
                                    .setMetadata(of(
                                            "correlation_id", correlationId,
                                            "filename", fileInputDetails.getFileName())),
                            null, Context.NONE);
            results.put(fileInputDetails.getFieldName(), fileId);
        }

        final JsonObjectBuilder objectBuilder =
                createObjectBuilder(inputEnvelope.payloadAsJsonObject());
        results.forEach((fieldName, fileId) ->
                objectBuilder.add(fieldName, fileId.toString()));

        final JsonEnvelope updatedEnvelope =
                envelopeFrom(inputEnvelope.metadata(), objectBuilder.build());
        return interceptorChain.processNext(
                interceptorContext.copyWithInput(updatedEnvelope));
    }

    private static String correlationIdFrom(final JsonEnvelope envelope) {
        final List<UUID> causation = envelope.metadata().causation();
        return causation.isEmpty()
                ? envelope.metadata().id().toString()
                : causation.stream()
                           .map(UUID::toString)
                           .collect(joining(","));
    }
}
```

> **Gotcha — use `randomUUID()` per upload.**
> A deterministic UUID derived from the filename would collide if the same filename is
> uploaded twice, silently overwriting the blob.

---

## 8. Downloading a file (event processor)

### Download methods that fail inside WildFly

| Method | Failure mode |
|---|---|
| `blobClient.downloadContent()` | `NullPointerException` — `getBlobLength()` reads `Content-Length`, null in JDK HTTP transport |
| `blobClient.downloadStream(outputStream)` | Same NPE via `getBlobLength()` |
| `blobClient.openInputStream()` | NPE — tries to parse `Content-Range` which is absent on full GET responses |
| `blobClient.getProperties().getBlobSize()` | Returns **0** — HEAD response Content-Length not exposed by JDK transport in WildFly |
| `blobContainerClient.listBlobs(...)` | `HttpResponseException: Deserialization Failed` — WildFly XML parser conflicts |
| `downloadStreamWithResponse(out, new BlobRange(0, 0), ...)` | HTTP 500 — `BlobRange(0, 0)` formats as `bytes=0--1` (invalid) |

### The working pattern

Use `downloadStreamWithResponse` with an explicit oversized `BlobRange`:

```java
blobClient.downloadStreamWithResponse(outputStream, new BlobRange(0, 1_000_000_000L),
        null, null, false, null, null);
```

This forces a range header (`bytes=0-999999999`), causing Azurite to reply with
HTTP 206 and a `Content-Range` header.  The SDK reads blob length from
`Content-Range` (not `Content-Length`), which works correctly inside WildFly.

See [`docs/streaming.md`](streaming.md) for both patterns:
- **Pattern 1** — `StreamingOutput` for serving a blob as an HTTP response
- **Pattern 2** — `PipedInputStream`/`PipedOutputStream` + `ManagedExecutorService` for processing

---

## 9. Integration testing

### Unit testing — mock final Azure SDK classes

`BlobContainerClient` and `BlobClient` are `final` classes.  Mockito's default
byte-buddy mock-maker cannot subclass final types, so any test that `@Mock`s these
classes will fail with:

```
org.mockito.exceptions.base.MockitoException:
  Cannot mock/spy class com.azure.storage.blob.BlobContainerClient
  Mockito cannot mock this class: class com.azure.storage.blob.BlobContainerClient.
  If you're not trying to mock a final class, ...
```

Fix — add `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`
(one line, no spaces) to every module whose tests mock Azure classes:

```
mock-maker-inline
```

`mock-maker-inline` uses JDK instrumentation rather than subclassing, so it
works on final types.  It is activated per-module by the file's presence — there
is no POM change required.

Typical `@Mock` declarations in tests that need this:

```java
@Mock
private BlobContainerClient blobContainerClient;

@Mock
private BlobClient blobClient;
```

The `when()` stub for `getBlobClient()` must also handle the fact that the blob
name contains a randomly generated `UUID` (generated inside the method under test).
Use `matches("internal/.*")` in the stub (specific pattern — `any()` is prohibited by
project convention) and `ArgumentCaptor<String>` in the verify to assert the blob name
starts with the expected path prefix and to extract the UUID for consistency checks:

```java
import static org.mockito.ArgumentMatchers.matches;

when(blobContainerClient.getBlobClient(matches("internal/.*"))).thenReturn(blobClient);

// ... call system under test ...

final ArgumentCaptor<String> blobNameCaptor = ArgumentCaptor.forClass(String.class);
verify(blobContainerClient).getBlobClient(blobNameCaptor.capture());
assertThat(blobNameCaptor.getValue(), startsWith("internal/"));
final UUID uploadedFileId = UUID.fromString(
        blobNameCaptor.getValue().substring("internal/".length()));

// Verify the same UUID reached the downstream call
verify(commandSender).processPocaEmail(uploadedFileId, pocaMailId, email, subject);
```

---

### Add `{context}-file-store-test-utils` to the IT module

In `{context}-integration-test/pom.xml`:

```xml
<dependency>
    <groupId>uk.gov.moj.cpp.{context}</groupId>
    <artifactId>{context}-file-store-test-utils</artifactId>
    <scope>test</scope>
</dependency>
```

### Helper class to seed blobs

Create an `AzureFileHelper` (or equivalent) in your IT test helpers:

```java
public class AzureFileHelper {

    private static final int AZURITE_BLOB_PORT = 10000;
    private static final String MY_CONTAINER = "{context}-files";
    private static final String AZURITE_ACCOUNT_NAME = "devstoreaccount1";
    // Azurite well-known public dev key — not a real secret
    private static final String AZURITE_ACCOUNT_KEY =
            "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";

    public static UUID uploadTestFile(final String pathPrefix,
                                      final String fileName,
                                      final byte[] content) {
        return BlobStoreTestHelper
                .forConnectionStringAndContainer(azuriteConnectionString(), MY_CONTAINER)
                .upload(pathPrefix, fileName, content);
    }

    private static String azuriteConnectionString() {
        return String.format(
                "DefaultEndpointsProtocol=http;AccountName=%s;AccountKey=%s;" +
                "BlobEndpoint=http://%s:%d/%s;",
                AZURITE_ACCOUNT_NAME, AZURITE_ACCOUNT_KEY,
                getHost(), AZURITE_BLOB_PORT, AZURITE_ACCOUNT_NAME);
    }
}
```

`getHost()` comes from `TestHostProvider` and resolves to the Docker host IP in CI.

### cleanProcessedEventTable in @BeforeEach

When each `@Test` method in an IT class cleans the database, it must clean
**both** the event log and the processed-event table.  If only `cleanEventLogTable`
is called, event numbers reset to 1 for the next test but the processed-event table
still contains rows with those numbers, causing a unique-constraint failure on the
second test:

```
ProcessedEventTrackingException: ERROR: duplicate key value violates unique constraint
    "processed_event_event_number_source_component_key"
```

Fix — every `cleanDatabase()` method must include both:

```java
private void cleanDatabase() {
    databaseCleaner.cleanEventLogTable(CONTEXT_NAME);
    databaseCleaner.cleanProcessedEventTable(CONTEXT_NAME);
    // any other cleaner calls
}
```

### Event-driven test flow

```java
// 1. Seed a blob into the inbox before triggering the event
final UUID fileId = AzureFileHelper.uploadTestFile(
        "inbox/notification-templates", "template.pdf", readPdfBytes("template.pdf"));

// 2. Trigger the event that causes the event-processor to download
publishNotificationRequestedEvent(notificationId, fileId);

// 3. Wait for the downstream event confirming success
final Optional<JsonEnvelope> notificationSentEvent = new EventListener()
        .withMaxWaitTime(50_000)
        .subscribe(NOTIFICATION_SENT)
        .run(() -> /* trigger action */)
        .popEvent(NOTIFICATION_SENT);

assertThat(notificationSentEvent.isPresent(), is(true));
```

---

## 10. CDI ambiguity — one core dependency per service WAR

### The problem — WELD-001409

If two modules bundled into the same WAR both contain an `AzureBlobContainerClientProducer`
with a `@Produces BlobContainerClient` method, Weld sees duplicate producers and fails
at deploy time:

```
WELD-001409: Ambiguous dependencies for type BlobContainerClient
  Qualifiers: [@Default]
  Possible dependencies:
    - Producer Method [BlobContainerClient] with qualifiers [@Dependent @Any]
        declared on AzureBlobContainerClientProducer (module-A)
    - Producer Method [BlobContainerClient] with qualifiers [@Dependent @Any]
        declared on AzureBlobContainerClientProducer (module-B)
```

### The fix

**Only ONE module in each WAR may depend on `{context}-file-store-core`.**

In the notification-notify design:
- `notificationnotify-event-processor` depends on `notificationnotify-file-store-core` — UC1 (`FileStorer`) used by `PocaEmailsTask`
- `notificationnotify-command-handler` depends on `notificationnotify-file-store-core` — UC2 (`FileIngestor`) used by `IngestFileCommandHandler`
- `notificationnotify-service` does NOT depend on `notificationnotify-file-store-core` directly

Each of `event-processor` and `command-handler` is a **separate WAR** deployment.
Because CDI wiring is scoped per-WAR, each WAR has its own `AzureBlobContainerClientProducer`
instance with no ambiguity.  The WELD-001409 problem only arises when two modules are
bundled into the **same** WAR.

If your context bundles multiple components into a single WAR, ensure only one module
in that WAR depends on `{context}-file-store-core`.

---

## 11. Gotchas and known problems

### Netty must be excluded from every Azure SDK dependency

WildFly 26 ships its own Netty modules.  Exclude from every `com.azure` dep:

```bash
mvn dependency:tree -Dincludes="com.azure:azure-core-http-netty"
# output must be empty
```

---

### `downloadContent()`, `downloadStream()`, `openInputStream()` all NPE

All three ultimately call `ModelHelper.getBlobLength()` which reads
`Content-Length`.  The JDK HTTP transport in WildFly does not provide
`Content-Length` for full GET responses → `NullPointerException`.

Fix: use `downloadStreamWithResponse` with an explicit non-zero `BlobRange` (§8).

---

### `BlobRange(0, 0)` produces an invalid range header

`new BlobRange(0, 0L)` produces `"bytes=0--1"` (end = offset + count − 1 = −1).
Azurite returns HTTP 500.  Always use a count ≥ 1; the reference uses `1_000_000_000L`.

---

### `listBlobs()` throws `HttpResponseException: Deserialization Failed`

The Azure SDK deserialises LIST XML using `azure-xml`, which conflicts with
WildFly's XML parser infrastructure.  Do not use `listBlobs()` inside WildFly.

---

### `createIfNotExists()` throws on Azurite — catch and continue

Azurite returns a `ContainerAlreadyExists` XML error response.  The JDK HTTP
transport cannot parse it as JSON.  Catch `RuntimeException` in `@PostConstruct`
and log a warning.  See the producer code in §3.

---

### `PSQLException: ERROR: relation "content" does not exist`

**Cause:** Old `cp-file-service` / `rest-adapter-file-service` /
`file-service-persistence` dependencies still on the classpath.  `ContentJdbcRepository`
fires and tries to write to the `content` table in the `fileservice` schema.

**Fix:** Remove all old file-service dependencies:

```xml
<!-- Remove these -->
<dependency>
    <groupId>uk.gov.justice.framework-generators</groupId>
    <artifactId>rest-adapter-file-service</artifactId>
</dependency>
<dependency>
    <groupId>uk.gov.justice.services</groupId>
    <artifactId>file-service-persistence</artifactId>
</dependency>
```

Add `rest-adapter-core` explicitly (previously transitive through
`rest-adapter-file-service`, provides `FileInputDetails`):

```xml
<dependency>
    <groupId>uk.gov.justice.framework-generators</groupId>
    <artifactId>rest-adapter-core</artifactId>
    <version>${cpp.framework.version}</version>
</dependency>
```

---

### After restarting WildFly, restart the socat container too

The `wildfly-to-haproxy-port-forward` socat container runs in WildFly's Docker
network namespace.  After `docker restart containers-cpp-wildfly-1`:

```bash
docker restart wildfly-to-haproxy-port-forward
```

If not restarted, Drools access-control rules fail:

```
Connect to localhost:8080 [localhost/127.0.0.1] failed: Connection refused
```

and every command returns HTTP 500.

---

### Hot-redeployment causes WildFly Metaspace OOM

After 3–4 hot-redeploys:

```bash
docker restart containers-cpp-wildfly-1
# wait for WildFly ready
docker exec containers-cpp-wildfly-1 truncate -s 0 \
    /opt/jboss/wildfly/standalone/log/server.log
docker restart wildfly-to-haproxy-port-forward
# then redeploy via jboss-cli.sh --command="deploy --force /tmp/{war}"
```

---

### `EventDiscoveryTimerBean` firing in the service WAR

If `java:global/event.processing.by.pull.mechanism.enabled=true`, the timer fires
in both the service WAR and the event-processor WAR.  Add a per-WAR override:

```xml
<simple name="java:/app/notificationnotify-service/event.processing.by.pull.mechanism.enabled"
        value="false"
        type="java.lang.String"/>
```

---

### Diagnosing Azurite problems

```bash
docker exec cpp-azurite cat /data/debug.log | tail -100
```

Look for: invalid range headers, `deserializeRangeHeader` errors, HEAD response
`content-length`, and full XML response bodies.
