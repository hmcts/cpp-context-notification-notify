package uk.gov.moj.cpp.notification.notify.filestore.azure;

import uk.gov.justice.services.common.configuration.Value;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

/**
 * JNDI-backed configuration bean for Azure Blob Storage.
 *
 * <p>Reads three per-application JNDI values from WildFly via the framework's
 * {@code @Value} annotation. {@code azure.filestore.endpoint} and
 * {@code azure.filestore.container-name} must always be present in
 * {@code standalone.xml}. {@code azure.filestore.connection-string} is
 * <strong>optional</strong> — it defaults to the sentinel value
 * {@code "DefaultAzureCredential"} when absent, which causes
 * {@link AzureBlobContainerClientProducer} to authenticate via
 * {@code DefaultAzureCredential} (Workload Identity on AKS).
 *
 * <p>The connection string must only be configured in environments that run
 * Azurite (the Azure Storage emulator used for local development and integration
 * testing). Production and staging deployments must omit the entry entirely —
 * no {@code azure.filestore.connection-string} value should appear in
 * production {@code standalone.xml}.
 *
 * <p>See {@code patterns/jndi.md} in {@code pe_arch_design_docs} for the full
 * per-environment reference.
 */
@ApplicationScoped
public class AzureBlobConfiguration {

    @Inject
    @Value(key = "azure.filestore.connection-string", defaultValue = "DefaultAzureCredential")
    private String connectionString;

    @Inject
    @Value(key = "azure.filestore.endpoint")
    private String endpoint;

    @Inject
    @Value(key = "azure.filestore.container-name")
    private String containerName;

    /**
     * Returns the raw {@code azure.filestore.connection-string} JNDI value.
     *
     * <p>Non-blank only in environments running Azurite (local development,
     * integration tests). When the JNDI entry is absent, this returns the sentinel
     * value {@code "DefaultAzureCredential"} — not a real connection string.
     *
     * <p>Use {@link #hasConnectionString()} to test whether a real Azurite
     * connection string has been configured, rather than inspecting this value
     * directly.
     *
     * @return the connection string, or {@code "DefaultAzureCredential"} when no
     *         JNDI entry is present
     */
    public String getConnectionString() {
        return connectionString;
    }

    /**
     * Returns {@code true} when a real Azurite connection string has been configured.
     *
     * <p>Returns {@code false} when the connection string is absent from JNDI
     * (defaulting to the {@code "DefaultAzureCredential"} sentinel), blank, or null.
     * In all {@code false} cases {@link AzureBlobContainerClientProducer} authenticates
     * via {@code DefaultAzureCredential} (Workload Identity on AKS).
     *
     * @return {@code true} only in Azurite-backed environments (local dev,
     *         integration tests)
     */
    public boolean hasConnectionString() {
        return connectionString != null && !connectionString.isBlank() && !"DefaultAzureCredential".equals(connectionString);
    }

    /**
     * Returns the Azure Blob Storage service endpoint URL.
     *
     * <p>Used when {@link #hasConnectionString()} returns {@code false}, i.e. in
     * production where Workload Identity (Entra ID Federated Identity Credential)
     * is used for authentication.
     * Example: {@code https://mystorage.blob.core.windows.net}.
     *
     * @return the storage account endpoint URL
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * Returns the name of the blob container owned by this service.
     *
     * <p>Each CPP service owns exactly one container. For notification-notify this
     * is {@code notificationnotify-files} (local) or
     * {@code notificationnotify-files-{env}} (AKS). The container is created at
     * startup via {@code createIfNotExists()} if it does not already exist.
     *
     * @return the container name
     */
    public String getContainerName() {
        return containerName;
    }
}
