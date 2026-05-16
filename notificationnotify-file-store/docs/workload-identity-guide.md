# BYOFS Workload Identity Wiring Guide

How to configure an AKS pod to authenticate to its Azure Blob Storage container
using Workload Identity — no client secrets, no connection strings in production.

**Audience:** Platform engineers and service teams onboarding a new context to BYO FileStore.

---

## How it works

Azure Workload Identity links a Kubernetes `ServiceAccount` to an Entra ID identity
(App Registration or User Assigned Managed Identity) via a **Federated Identity
Credential (FIC)**.  When a pod runs with that `ServiceAccount`, the AKS Workload
Identity Webhook automatically injects a projected service account token and four
environment variables.  The Azure SDK's `DefaultAzureCredential` reads those env
vars and exchanges the token for a short-lived Entra access token (~1 hour TTL).

In `AzureBlobContainerClientProducer`, the production path activates automatically
when `azure.storage.connection-string` is blank:

```java
new BlobServiceClientBuilder()
    .credential(new DefaultAzureCredentialBuilder().build())
    .endpoint(azureBlobConfiguration.getEndpoint())
    .buildClient();
```

No code changes are needed in the service — only infrastructure configuration.

---

## Prerequisites

| Requirement | How to verify |
|---|---|
| AKS cluster has OIDC issuer enabled | `az aks show -g <rg> -n <cluster> --query "oidcIssuerProfile.enabled"` → `true` |
| Workload Identity Webhook installed | `kubectl get pods -n azure-workload-identity-system` |
| Azure Blob Storage container provisioned for the service | BYOFS-1.1 (Bicep IaC) |
| Entra App Registration (or User Assigned Managed Identity) created | Platform infrastructure team |

---

## Step 1 — Create the Federated Identity Credential

```bash
az identity federated-credential create \
  --name <service-name>-aks-<env> \
  --identity-name <managed-identity-name> \
  --resource-group <resource-group> \
  --issuer <aks-oidc-issuer-url> \
  --subject "system:serviceaccount:<namespace>:<service-account-name>" \
  --audience api://AzureADTokenExchange

# Get the OIDC issuer URL:
az aks show -g <rg> -n <cluster> --query "oidcIssuerProfile.issuerUrl" -o tsv
```

---

## Step 2 — Annotate the Kubernetes ServiceAccount

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: <service-account-name>
  namespace: <namespace>
  annotations:
    azure.workload.identity/client-id: "<entra-client-id>"
```

---

## Step 3 — Label the Deployment pod template

```yaml
spec:
  template:
    metadata:
      labels:
        azure.workload.identity/use: "true"
    spec:
      serviceAccountName: <service-account-name>
```

---

## Step 4 — What the Webhook injects

| Variable | Purpose |
|---|---|
| `AZURE_CLIENT_ID` | Entra client ID from the ServiceAccount annotation |
| `AZURE_TENANT_ID` | AKS cluster's Entra tenant |
| `AZURE_FEDERATED_TOKEN_FILE` | Path to the projected token file |
| `AZURE_AUTHORITY_HOST` | `https://login.microsoftonline.com/` |

`DefaultAzureCredential` reads these automatically.

---

## Step 5 — Configure JNDI for production

In `standalone.xml` (or the AKS config map), set the three JNDI entries.  Leave
`connection-string` blank to activate `DefaultAzureCredential`:

```xml
<simple name="java:/app/notificationnotify-event-processor/azure.storage.connection-string"
        value=""
        type="java.lang.String"/>
<simple name="java:/app/notificationnotify-event-processor/azure.storage.endpoint"
        value="https://<storage-account-name>.blob.core.windows.net"
        type="java.lang.String"/>
<simple name="java:/app/notificationnotify-event-processor/azure.storage.container-name"
        value="notificationnotify-files-<env>"
        type="java.lang.String"/>
```

See [jndi.md](jndi.md) for the full per-environment reference.

---

## Step 6 — Assign the RBAC role

Grant the Entra identity `Storage Blob Data Contributor` scoped to the container:

```bash
az role assignment create \
  --assignee "<entra-client-id>" \
  --role "Storage Blob Data Contributor" \
  --scope "/subscriptions/<sub>/resourceGroups/<rg>/providers/Microsoft.Storage/storageAccounts/<account>/blobServices/default/containers/<container-name>"
```

Scoping to the container (not the account) is the least-privilege requirement.

---

## Verification

```bash
# 1. Check env vars are injected
kubectl exec -n <namespace> <pod-name> -- env | grep AZURE

# 2. Check the projected token file exists
kubectl exec -n <namespace> <pod-name> -- ls -la /var/run/secrets/azure/tokens/
```

After deployment, look for container client initialisation in WildFly `server.log`.
A failed token exchange appears as:

```
CredentialUnavailableException: WorkloadIdentityCredential authentication unavailable
```

Common cause: the FIC subject (`system:serviceaccount:<ns>:<sa>`) does not exactly
match the running ServiceAccount.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `AZURE_CLIENT_ID` env var absent | Pod label missing or Webhook not running | Add label; check `kubectl get pods -n azure-workload-identity-system` |
| `CredentialUnavailableException` | FIC subject mismatch or wrong OIDC issuer URL | Re-check FIC `--subject`; verify OIDC issuer URL |
| `AuthorizationPermissionMismatch` on blob write | RBAC role assignment missing or wrong scope | Check `az role assignment list --assignee <client-id>` |
| Container auto-create fails | `Storage Blob Data Contributor` cannot create containers (only blobs within existing container) | Pre-provision the container via BYOFS-1.1 Bicep IaC |
| Works in dev but fails in AKS | `azure.storage.connection-string` JNDI entry is non-blank in AKS config | Set it to `""` — any non-blank value bypasses `DefaultAzureCredential` |

---

## Links

- [jndi.md](jndi.md) — per-environment JNDI values and onboarding template
- [metadata-convention.md](metadata-convention.md) — required blob metadata keys
- [Microsoft: Workload Identity overview](https://azure.github.io/azure-workload-identity/docs/)
- [Microsoft: Use Workload Identity with AKS](https://learn.microsoft.com/en-us/azure/aks/workload-identity-overview)
