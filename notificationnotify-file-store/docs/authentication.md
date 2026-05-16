# BYO FileStore — Authentication Reference

How CPP context services authenticate to Azure Blob Storage — dual-mode pattern,
CDI wiring, JNDI keys, and the multi-container qualifier approach.

**Audience:** Developers migrating a context to BYO FileStore, or diagnosing
authentication failures in an existing implementation.

---

## How authentication is selected

`AzureBlobContainerClientProducer` (or its per-context equivalent) checks one JNDI
value at startup and picks the credential accordingly:

```
azure.storage.connection-string
    non-blank  →  connect using the connection string directly  (Azurite / dev)
    blank      →  use DefaultAzureCredential + azure.storage.endpoint  (AKS / prod)
```

No code changes are needed when moving from development to production — only the
JNDI value changes.

```java
protected BlobContainerClient buildBlobContainerClient(final AzureBlobConfiguration config) {
    final String connectionString = config.getConnectionString();
    final BlobServiceClient blobServiceClient;
    if (connectionString != null && !connectionString.isBlank()) {
        blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();
    } else {
        blobServiceClient = new BlobServiceClientBuilder()
                .credential(new DefaultAzureCredentialBuilder().build())
                .endpoint(config.getEndpoint())
                .buildClient();
    }
    return blobServiceClient.getBlobContainerClient(config.getContainerName());
}
```

`DefaultAzureCredential` probes a chain of credential sources in order.  On AKS with
Workload Identity configured, it resolves to `WorkloadIdentityCredential` using the
projected service account token injected by the AKS Workload Identity Webhook.  No
explicit credential type needs to be named in code.

---

## Pattern 1 — Single container per WAR (notification-notify, reference-data)

Each service owns one container.  One `AzureBlobConfiguration` + one
`AzureBlobContainerClientProducer` in the service's `{context}-file-store-core`
module produces a single `BlobContainerClient` for injection anywhere in that WAR.

### Configuration class

Reads three JNDI keys via the framework's `@Value` annotation:

```java
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

    // getters omitted
}
```

### Producer class

```java
@ApplicationScoped
public class AzureBlobContainerClientProducer {

    @Inject
    private Logger logger;

    @Inject
    private AzureBlobConfiguration azureBlobConfiguration;

    private BlobContainerClient blobContainerClient;

    @PostConstruct
    public void initialise() {
        blobContainerClient = buildBlobContainerClient(azureBlobConfiguration);
        try {
            blobContainerClient.createIfNotExists();
        } catch (final RuntimeException e) {
            logger.warn("createIfNotExists failed for container '{}' — assuming it already exists: {}",
                    azureBlobConfiguration.getContainerName(), e.getMessage());
        }
    }

    @Produces
    @Dependent          // BlobContainerClient is final — Weld cannot proxy it for @ApplicationScoped
    public BlobContainerClient blobContainerClient() {
        return blobContainerClient;
    }

    protected BlobContainerClient buildBlobContainerClient(final AzureBlobConfiguration config) {
        // ... dual-mode logic as above
    }
}
```

> **`@Dependent` not `@ApplicationScoped` on the producer method.**
> `BlobContainerClient` is a `final` class.  Weld cannot create a proxy subclass for it,
> so annotating the `@Produces` method with `@ApplicationScoped` causes `WELD-001410`
> at deploy time.  Use `@Dependent` — the single shared instance is created once in
> `@PostConstruct` and returned on every injection point.

### Injection at call sites

```java
@Inject
private BlobContainerClient blobContainerClient;
```

### JNDI keys for Pattern 1

Three keys required: `azure.storage.connection-string`, `azure.storage.endpoint`, `azure.storage.container-name`. See [jndi.md](jndi.md) for the full reference including per-environment values, `standalone.xml` examples, and the global shortcut pattern.

**Implemented by:** `cpp-context-notification-notify`, `cpp-context-reference-data`

---

## Pattern 2 — Multiple containers in the same WAR (CDI qualifier approach)

Some services own more than one container — for example, `cpp-context-mi-reportdata`
uses a separate container for MISL extract files and a separate container for live
report files, and both are bundled into the same `mireportdata-service.war`.

A single unqualified `@Produces BlobContainerClient` would cause a Weld ambiguity
error (`WELD-001409`) because two beans of the same type would exist in the archive.
The fix is to use CDI qualifiers to distinguish them.

### Step 1 — Define a qualifier per container

One qualifier annotation per storage account/container.  Place these in a shared
module (e.g. `{context}-common-service`) so both the producers and the injection
points can import them.

```java
@Qualifier
@Retention(RUNTIME)
@Target({FIELD, METHOD, PARAMETER, TYPE})
public @interface MislExtract {
}
```

```java
@Qualifier
@Retention(RUNTIME)
@Target({FIELD, METHOD, PARAMETER, TYPE})
public @interface LiveReport {
}
```

### Step 2 — One configuration class per container

Each uses its own JNDI key prefix:

```java
@ApplicationScoped
public class MislExtractAzureBlobConfiguration {

    @Inject
    @Value(key = "mireportdata.rotasl.storage.connection-string")
    private String connectionString;

    @Inject
    @Value(key = "mireportdata.rotasl.storage.endpoint")
    private String endpoint;

    @Inject
    @Value(key = "mireportdata.rotasl.storage.container-name")
    private String containerName;

    // getters omitted
}
```

```java
@ApplicationScoped
public class LiveReportAzureBlobConfiguration {

    @Inject
    @Value(key = "mireportdata.livereport.storage.connection-string")
    private String connectionString;

    @Inject
    @Value(key = "mireportdata.livereport.storage.endpoint")
    private String endpoint;

    @Inject
    @Value(key = "mireportdata.livereport.storage.container-name")
    private String containerName;

    // getters omitted
}
```

### Step 3 — One producer per container, annotated with its qualifier

Apply the qualifier to the `@Produces` method:

```java
@ApplicationScoped
public class MislExtractAzureBlobContainerClientProducer {

    @Inject
    private MislExtractAzureBlobConfiguration configuration;

    private BlobContainerClient blobContainerClient;

    @PostConstruct
    public void initialise() {
        blobContainerClient = buildBlobContainerClient(configuration);
        // createIfNotExists with warn-on-failure ...
    }

    @Produces
    @Dependent
    @MislExtract                  // ← qualifier on the producer method
    public BlobContainerClient blobContainerClient() {
        return blobContainerClient;
    }

    protected BlobContainerClient buildBlobContainerClient(
            final MislExtractAzureBlobConfiguration config) {
        // ... dual-mode logic
    }
}
```

Same structure for `LiveReportAzureBlobContainerClientProducer` with `@LiveReport`.

### Step 4 — Inject with the qualifier at call sites

```java
@Inject
@MislExtract
private BlobContainerClient mislExtractBlobContainerClient;
```

```java
@Inject
@LiveReport
private BlobContainerClient liveReportBlobContainerClient;
```

Weld resolves each injection point unambiguously because the qualifier narrows the
type to exactly one matching producer.

### JNDI keys for Pattern 2

Because both containers are in the same WAR, they cannot share the same key names.
Use a context-specific prefix on each key, resolved via `java:global/`:

| Key | Dev (Azurite) | Prod (AKS) |
|---|---|---|
| `mireportdata.rotasl.storage.connection-string` | Full Azurite connection string (`host.docker.internal:10000`) | `""` |
| `mireportdata.rotasl.storage.endpoint` | `http://host.docker.internal:10000/devstoreaccount1` | `https://{account}.blob.core.windows.net` |
| `mireportdata.rotasl.storage.container-name` | `misl-extract-blob-container` | `misl-extract-blob-container-{env}` |
| `mireportdata.livereport.storage.connection-string` | Full Azurite connection string (`127.0.0.1:10000`) | `""` |
| `mireportdata.livereport.storage.endpoint` | `http://127.0.0.1:10000/devstoreaccount1` | `https://{account}.blob.core.windows.net` |
| `mireportdata.livereport.storage.container-name` | `misl-live-report-blob-container` | `misl-live-report-blob-container-{env}` |

> **Why `127.0.0.1` for live-report but `host.docker.internal` for extract?**
> The live-report producer generates SAS URIs that embed the `BlobEndpoint` hostname.
> Those URIs are returned to IT tests running on the Mac host, which cannot resolve
> `host.docker.internal`.  Using `127.0.0.1:10000` (which Azurite exposes on the host
> via Docker port mapping) makes the SAS URIs resolvable from both inside WildFly
> (via the azurite socat container) and from the test host.

In `standalone.xml`:

```xml
<!-- MISL extract blob storage -->
<simple name="java:global/mireportdata.rotasl.storage.connection-string"
        value="{azurite-connection-string-with-host.docker.internal}"
        type="java.lang.String"/>
<simple name="java:global/mireportdata.rotasl.storage.endpoint"
        value="http://host.docker.internal:10000/devstoreaccount1"
        type="java.lang.String"/>
<simple name="java:global/mireportdata.rotasl.storage.container-name"
        value="misl-extract-blob-container"
        type="java.lang.String"/>

<!-- Live report blob storage -->
<simple name="java:global/mireportdata.livereport.storage.connection-string"
        value="{azurite-connection-string-with-127.0.0.1}"
        type="java.lang.String"/>
<simple name="java:global/mireportdata.livereport.storage.endpoint"
        value="http://127.0.0.1:10000/devstoreaccount1"
        type="java.lang.String"/>
<simple name="java:global/mireportdata.livereport.storage.container-name"
        value="misl-live-report-blob-container"
        type="java.lang.String"/>
```

**Implemented by:** `cpp-context-mi-reportdata`

---

## Production: Workload Identity setup

Once the JNDI `connection-string` is blank, `DefaultAzureCredential` activates.
For it to resolve successfully on AKS, the pod's ServiceAccount must be linked to
an Entra ID identity via a Federated Identity Credential (FIC).

See [workload-identity-guide.md](workload-identity-guide.md) for the full setup:
AKS OIDC issuer, FIC creation, ServiceAccount annotation, pod label, env vars
injected by the webhook, and `Storage Blob Data Contributor` role assignment.

---

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `WELD-001409: Ambiguous dependencies for BlobContainerClient` | Two producers of unqualified `BlobContainerClient` in the same WAR — use the CDI qualifier pattern (Pattern 2) |
| `WELD-001410: Cannot proxy BlobContainerClient` | `@Produces` method is `@ApplicationScoped` — change to `@Dependent` |
| `NamingException` at deploy: `azure.storage.connection-string not bound` | JNDI entry missing in `standalone.xml` for the deployed WAR name |
| `CredentialUnavailableException: WorkloadIdentityCredential authentication unavailable` | AKS: FIC subject does not match running ServiceAccount, or Workload Identity Webhook not installed — see [workload-identity-guide.md](workload-identity-guide.md) |
| `AuthorizationPermissionMismatch` on blob write | RBAC role assignment missing or wrong scope — verify `Storage Blob Data Contributor` at container level |
| Works in dev (connection string) but fails in AKS | `connection-string` JNDI value is non-blank in AKS config — set it to `""` |
| `createIfNotExists()` throws on startup | Expected on Azurite if container already exists — the producer catches `RuntimeException` and logs a warning; not an error |

---

## Related documents

| Document | What it covers |
|---|---|
| [workload-identity-guide.md](workload-identity-guide.md) | AKS Workload Identity infrastructure wiring (FIC, ServiceAccount, pod labels, RBAC) |
| [jndi.md](jndi.md) | Full per-environment JNDI value reference; global shortcut pattern |
| [azure-blobstore-migration.md](azure-blobstore-migration.md) | End-to-end migration guide including CDI producer setup |
| [implementation-status.md](implementation-status.md) | Which contexts have completed the auth migration |
