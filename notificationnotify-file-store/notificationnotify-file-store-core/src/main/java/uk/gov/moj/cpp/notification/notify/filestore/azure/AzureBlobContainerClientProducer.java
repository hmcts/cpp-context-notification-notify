package uk.gov.moj.cpp.notification.notify.filestore.azure;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import static java.lang.String.format;

import com.azure.core.exception.HttpResponseException;
import com.azure.core.http.jdk.httpclient.JdkHttpClientBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.slf4j.Logger;

/**
 * CDI producer that builds and exposes a {@link BlobContainerClient} for injection.
 *
 * <p>The client is constructed once in {@link #initialise()} from JNDI-backed
 * {@link AzureBlobConfiguration} values and cached. Authentication resolves in this order:
 * <ol>
 *   <li>If {@code azure.filestore.connection-string} is set to a real connection string (not
 *       the {@code "DefaultAzureCredential"} sentinel), a connection-string client is built —
 *       intended for local development against Azurite only.</li>
 *   <li>Otherwise, {@link com.azure.identity.DefaultAzureCredential} is used with
 *       {@code azure.filestore.endpoint} — on AKS this resolves automatically to the pod's
 *       Workload Identity (Entra ID Federated Identity Credential).</li>
 * </ol>
 *
 * <p><strong>Why {@code @Dependent} on the producer method:</strong>
 * {@link BlobContainerClient} is a {@code final} class. Weld cannot create a proxy for
 * final types, so {@code @ApplicationScoped} on the producer method would fail with
 * {@code WELD-001410} at deployment. {@code @Dependent} injects the real instance
 * directly; the single shared instance is held by this {@code @ApplicationScoped} bean
 * and returned on every injection point.
 *
 * <p><strong>One producer per WAR:</strong> only one module per WAR deployment may depend
 * on {@code notificationnotify-file-store-core}. Multiple modules in the same WAR would
 * create duplicate {@code @Produces BlobContainerClient} methods and cause a Weld
 * deployment failure. {@code notificationnotify-event-processor.war} and
 * {@code notificationnotify-service.war} are separate WARs and may each declare the
 * dependency independently.
 */
@ApplicationScoped
public class AzureBlobContainerClientProducer {

    private static final int HTTP_CONFLICT = 409;

    @Inject
    private Logger logger;

    @Inject
    private AzureBlobConfiguration azureBlobConfiguration;

    private BlobContainerClient blobContainerClient;

    /**
     * Builds the {@link BlobContainerClient} and ensures the target container exists.
     *
     * <p>A {@link RuntimeException} from {@code createIfNotExists()} is caught and logged as
     * a warning rather than propagated — in non-dev environments the container is pre-provisioned
     * by Bicep IaC and the call is expected to return a 409 Conflict.
     */
    @PostConstruct
    public void initialise() {
        blobContainerClient = buildBlobContainerClient(azureBlobConfiguration);
        try {
            blobContainerClient.createIfNotExists();
        } catch (final HttpResponseException e) {
            if (e.getResponse() != null && e.getResponse().getStatusCode() == HTTP_CONFLICT) {
                logger.warn("BlobContainerClient.createIfNotExists returned 409 Conflict for container '{}' — container already exists",
                        azureBlobConfiguration.getContainerName());
            } else {
                throw new AzureBlobContainerClientCreationException(
                        format("Failed to create BlobContainerClient for container '%s'", azureBlobConfiguration.getContainerName()), e);
            }
        }
    }

    /**
     * Returns the shared {@link BlobContainerClient} for CDI injection.
     *
     * <p>{@link BlobContainerClient} is a {@code final} class — Weld cannot subclass it to
     * create a proxy, so {@code @ApplicationScoped} on this producer method would fail with
     * {@code WELD-001410} at deployment. {@code @Dependent} injects the real instance directly. The
     * single shared instance is created once in {@link #initialise()} and returned here.
     *
     * @return the configured {@link BlobContainerClient}
     */
    @Produces
    @Dependent
    public BlobContainerClient blobContainerClient() {
        return blobContainerClient;
    }

    /**
     * Constructs a {@link BlobContainerClient} from the supplied configuration.
     *
     * <p>{@code protected} visibility allows the method to be overridden in unit tests via a
     * Mockito spy so that test code can substitute a mock client without making network calls.
     *
     * @param configuration JNDI-backed configuration supplying connection string, endpoint, and
     *                      container name
     * @return a {@link BlobContainerClient} targeting the configured container (no network call has
     * been made at this point)
     */
    protected BlobContainerClient buildBlobContainerClient(final AzureBlobConfiguration configuration) {
        final JdkHttpClientBuilder httpClientBuilder = new JdkHttpClientBuilder()
                .connectionTimeout(configuration.getConnectionTimeout())
                .responseTimeout(configuration.getResponseTimeout());

        if (configuration.hasConnectionString()) {
            return new BlobServiceClientBuilder()
                    .httpClient(httpClientBuilder.build())
                    .connectionString(configuration.getConnectionString())
                    .buildClient()
                    .getBlobContainerClient(configuration.getContainerName());
        }

        return new BlobServiceClientBuilder()
                .httpClient(httpClientBuilder.build())
                .credential(new DefaultAzureCredentialBuilder().build())
                .endpoint(configuration.getEndpoint())
                .buildClient()
                .getBlobContainerClient(configuration.getContainerName());
    }
}
