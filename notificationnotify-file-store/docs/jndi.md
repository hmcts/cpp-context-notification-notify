# file-store JNDI Configuration Reference

This document lists every JNDI value consumed by the `{context}-file-store-core`
CDI beans, the scope of each entry, and the values expected in each environment.

This is the **notification-notify reference**.  Values use `notificationnotify`
as the service name.  Substitute your own context's WAR names when onboarding
a new service.

---

## Scope conventions

| Scope | JNDI prefix | Meaning |
|---|---|---|
| **App-specific** | `java:/app/{service-name}/` | Resolved per deployment unit — each WAR that embeds file-store-core declares its own values under its own service name |
| **Global** | `java:global/` | Shared across all deployments in the WildFly instance |

All file-store values are **app-specific**.  There are no global entries required
by file-store-core — though the global shortcut pattern (see below) can reduce
duplication when multiple WARs share the same Azurite endpoint.

---

## Values

### `azure.storage.connection-string`

| Property | Value |
|---|---|
| **JNDI name** | `java:/app/{service-name}/azure.storage.connection-string` |
| **Type** | `java.lang.String` |
| **Scope** | App-specific |
| **Default in code** | None — must be configured in every environment |
| **Class** | `AzureBlobConfiguration` |

**Behaviour:** If non-blank, `AzureBlobContainerClientProducer` connects using the
connection string directly (Azurite / dev).  If blank or empty (`""`), it falls
back to `DefaultAzureCredential` + `azure.storage.endpoint` — the correct mode
for AKS deployments using Workload Identity.

| Environment | Value |
|---|---|
| Local Docker (`cpp-developers-docker`) | Full Azurite connection string — see below |
| AKS (staging / production) | `""` (empty string — triggers Workload Identity path) |

---

### `azure.storage.endpoint`

| Property | Value |
|---|---|
| **JNDI name** | `java:/app/{service-name}/azure.storage.endpoint` |
| **Type** | `java.lang.String` |
| **Scope** | App-specific |
| **Default in code** | None — must be configured in every environment |
| **Class** | `AzureBlobConfiguration` |

**Behaviour:** Used only when `azure.storage.connection-string` is blank.  Must be
the full URL of the Azure Storage account.

| Environment | Value |
|---|---|
| Local Docker | `http://cpp-azurite:10000/devstoreaccount1` |
| AKS (staging / production) | `https://{account-name}.blob.core.windows.net` |

---

### `azure.storage.container-name`

| Property | Value |
|---|---|
| **JNDI name** | `java:/app/{service-name}/azure.storage.container-name` |
| **Type** | `java.lang.String` |
| **Scope** | App-specific |
| **Default in code** | None — must be configured in every environment |
| **Class** | `AzureBlobConfiguration` |

**Behaviour:** The name of the Blob Storage container owned by this service.  One
container per platform service, per environment.

| Environment | Value (notification-notify) |
|---|---|
| Local Docker | `notificationnotify-files` |
| AKS (staging / production) | `notificationnotify-files-{env}` |

---

## notification-notify `standalone.xml` entries (local Docker)

The `byo-file-store` branch of `cpp-developers-docker` uses global lookups so the
Azurite connection string and endpoint are not repeated per service:

```xml
<!-- notificationnotify: Azure Blob Storage (file-store) -->
<!-- connection-string and endpoint resolve via cpp.azure.storage.* globals -->
<lookup name="java:/app/notificationnotify-event-processor/azure.storage.connection-string"
        lookup="java:global/cpp.azure.storage.connection-string"/>
<lookup name="java:/app/notificationnotify-event-processor/azure.storage.endpoint"
        lookup="java:global/cpp.azure.storage.endpoint"/>
<simple name="java:/app/notificationnotify-event-processor/azure.storage.container-name"
        value="notificationnotify-files" type="java.lang.String"/>

<!-- notificationnotify-service: connection-string and endpoint via globals; container-name from event-processor -->
<lookup name="java:/app/notificationnotify-service/azure.storage.connection-string"
        lookup="java:global/cpp.azure.storage.connection-string"/>
<lookup name="java:/app/notificationnotify-service/azure.storage.endpoint"
        lookup="java:global/cpp.azure.storage.endpoint"/>
<lookup name="java:/app/notificationnotify-service/azure.storage.container-name"
        lookup="java:/app/notificationnotify-event-processor/azure.storage.container-name"/>
```

The `java:global/cpp.azure.storage.connection-string` global resolves to the standard
Azurite development connection string (fixed public key — not a real secret, safe to commit).

---

## Global shortcut pattern (in use on `byo-file-store` branch)

The `byo-file-store` branch of `cpp-developers-docker` uses global entries so the
Azurite connection string and endpoint are declared once and looked up by every service.
The global key names are prefixed `cpp.azure.storage.*` to avoid collisions:

```xml
<!-- Declared once — all per-service connection-string entries look this up -->
<lookup name="java:global/cpp.azure.storage.connection-string"
        lookup="java:global/dcs.document.azure.storage.connection-string"/>

<!-- Azurite blob endpoint -->
<simple name="java:global/cpp.azure.storage.endpoint"
        value="http://cpp-azurite:10000/devstoreaccount1" type="java.lang.String"/>

<!-- Per-service: look up the shared values; own container-name is always a simple entry -->
<lookup name="java:/app/{context}-event-processor/azure.storage.connection-string"
        lookup="java:global/cpp.azure.storage.connection-string"/>
<lookup name="java:/app/{context}-event-processor/azure.storage.endpoint"
        lookup="java:global/cpp.azure.storage.endpoint"/>
<simple name="java:/app/{context}-event-processor/azure.storage.container-name"
        value="{context}-files"
        type="java.lang.String"/>
```

The container name is always service-specific — never look it up globally.

---

## Adding entries for a new service

When a new context embeds `{context}-file-store-core`, add three entries for
each WAR that needs blob access, substituting its own service name and container:

```xml
<simple name="java:/app/{service-name}/azure.storage.connection-string"
        value="..."
        type="java.lang.String"/>
<simple name="java:/app/{service-name}/azure.storage.endpoint"
        value="..."
        type="java.lang.String"/>
<simple name="java:/app/{service-name}/azure.storage.container-name"
        value="{service-name}-files"
        type="java.lang.String"/>
```

Container name convention: `{context}-files` (local) / `{context}-files-{env}` (AKS).
