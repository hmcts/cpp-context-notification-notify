package uk.gov.moj.cpp.notification.notify.filestore.azure;

import uk.gov.justice.services.common.configuration.Value;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

/**
 * JNDI-backed configuration bean for Azure Blob Storage.
 *
 * <p>Reads three per-application JNDI values injected via the framework's {@code @Value}
 * mechanism. All three keys must be present in {@code standalone.xml} under the
 * application's {@code <bindings>} subsystem entry — missing entries cause a
 * {@code NamingException} at WAR deploy time, not at startup.
 *
 * <p>See {@code docs/JNDI.md} for the full reference including per-environment values
 * and the global JNDI shortcut pattern used to share the connection string and endpoint
 * across all notificationnotify WARs.
 */
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

    /**
     * Returns the Azure Blob Storage connection string.
     *
     * <p>Non-blank only in local development (Azurite). In production on AKS this value is
     * blank and {@link AzureBlobContainerClientProducer} falls back to
     * {@code DefaultAzureCredential} with the endpoint instead.
     *
     * @return the connection string, or a blank/null value in production environments
     */
    public String getConnectionString() {
        return connectionString;
    }

    /**
     * Returns the Azure Blob Storage service endpoint URL.
     *
     * <p>Used when {@link #getConnectionString()} is absent, i.e. in production where
     * Workload Identity (Entra ID Federated Identity Credential) is used for auth.
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
     * <p>Each CPP service owns exactly one container. For notificationnotify this is
     * {@code notificationnotify-files}. The container is created at startup via
     * {@code createIfNotExists()} if it does not already exist.
     *
     * @return the container name
     */
    public String getContainerName() {
        return containerName;
    }
}
