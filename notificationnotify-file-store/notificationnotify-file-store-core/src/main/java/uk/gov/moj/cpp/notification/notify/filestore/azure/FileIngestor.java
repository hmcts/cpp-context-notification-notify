package uk.gov.moj.cpp.notification.notify.filestore.azure;

import static java.util.Map.of;

import com.azure.core.util.polling.SyncPoller;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobCopyInfo;
import com.azure.storage.blob.options.BlobBeginCopyOptions;

import java.net.URI;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

/**
 * CDI bean that ingests a blob from a peer service's container into this service's own Azure Blob
 * container via an async-under-the-hood copy (UC2 ownership transfer, BYOFS v7).
 *
 * <p>The copy is performed using {@code beginCopy(BlobBeginCopyOptions).waitForCompletion()}.
 * Azure Storage pulls the bytes directly from the source URI — they never transit this application
 * server. The receiver's managed identity must hold {@code Storage Blob Data Reader} on the owner's
 * container (BYOFS-2.1). BYOFS-1.3 metadata ({@code correlation_id} + {@code filename}) is set
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
     * @param sourceUri   canonical URI of the source blob (no SAS token; RBAC read access required)
     */
    public void ingest(final StoragePath storagePath,
                       final UUID fileId,
                       final UUID correlationId,
                       final String filename,
                       final URI sourceUri) {
        final String blobName = storagePath.blobName(fileId);
        final SyncPoller<BlobCopyInfo, Void> poller = blobContainerClient.getBlobClient(blobName)
                .beginCopy(new BlobBeginCopyOptions(sourceUri.toString())
                        .setMetadata(of("correlation_id", correlationId.toString(),
                                "filename", filename)));
        poller.waitForCompletion();
        logger.info("Ingested blob '{}' sourceUri='{}' correlationId='{}' filename='{}'",
                blobName, sourceUri, correlationId, filename);
    }
}
