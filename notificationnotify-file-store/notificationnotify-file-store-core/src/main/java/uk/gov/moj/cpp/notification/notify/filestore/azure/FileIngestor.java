package uk.gov.moj.cpp.notification.notify.filestore.azure;

import static com.azure.core.util.Context.NONE;
import static java.util.Map.of;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.options.BlobCopyFromUrlOptions;

import java.net.URI;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

/**
 * CDI bean that ingests a blob from a remote SAS URI into this service's own Azure Blob
 * container via a server-side copy (UC2 ownership transfer).
 *
 * <p>The copy is performed synchronously using {@code copyFromUrlWithResponse}, which instructs
 * Azure Storage to pull the bytes directly from the source URI — they never transit this
 * application server. BYOFS-1.3 metadata ({@code correlation_id} + {@code filename}) is set
 * atomically on the destination blob.
 */
@ApplicationScoped
public class FileIngestor {

    @Inject
    @SuppressWarnings("squid:S1312")
    private Logger logger;

    @Inject
    private BlobContainerClient blobContainerClient;

    /**
     * Copies the blob at {@code sourceUri} into {@code storagePath/{fileId}} in this service's
     * container, setting {@code correlation_id} and {@code filename} metadata atomically.
     *
     * @param storagePath path-prefix for the destination blob
     * @param fileId      UUID that identifies the blob within the prefix
     * @param correlationId business correlation ID to record on the destination blob
     * @param filename    human-readable filename stored in blob metadata
     * @param sourceUri   read-SAS URL of the source blob (from the owning service)
     */
    public void ingest(final StoragePath storagePath,
                       final UUID fileId,
                       final UUID correlationId,
                       final String filename,
                       final URI sourceUri) {
        final String blobName = storagePath.blobName(fileId);
        blobContainerClient.getBlobClient(blobName)
                .copyFromUrlWithResponse(
                        new BlobCopyFromUrlOptions(sourceUri.toString())
                                .setMetadata(of("correlation_id", correlationId.toString(),
                                        "filename", filename)),
                        null, NONE);
        logger.info("Ingested blob '{}' sourceUri='{}' correlationId='{}' filename='{}'",
                blobName, sourceUri, correlationId, filename);
    }
}
