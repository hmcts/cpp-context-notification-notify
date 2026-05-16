package uk.gov.moj.cpp.notification.notify.filestore.azure;

import static com.azure.core.util.BinaryData.fromBytes;
import static com.azure.core.util.Context.NONE;
import static java.util.Map.of;
import static java.util.UUID.randomUUID;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.options.BlobParallelUploadOptions;

import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

/**
 * CDI bean that stores blobs into the service's own Azure Blob container, enforcing
 * the BYOFS-1.3 metadata convention ({@code correlation_id} + {@code filename} on every blob).
 *
 * <p>Blob names follow the {@link StoragePath} path-prefix convention:
 * {@code {prefix}/{fileId}}.
 */
@ApplicationScoped
public class FileStorer {

    @Inject
    @SuppressWarnings("squid:S1312")
    private Logger logger;

    @Inject
    private BlobContainerClient blobContainerClient;

    /**
     * Uploads {@code content} to {@code storagePath/{randomUUID}}, sets {@code correlation_id}
     * and {@code filename} metadata per the BYOFS-1.3 convention, and returns the generated
     * file ID.
     *
     * @param storagePath path-prefix for the blob (e.g. {@link StoragePath#internal()})
     * @param correlationId business correlation ID to record on the blob
     * @param filename      human-readable original filename stored in blob metadata
     * @param content       raw bytes to upload
     * @return the UUID assigned as the blob's name under the path prefix
     */
    public UUID store(final StoragePath storagePath,
                      final UUID correlationId,
                      final String filename,
                      final byte[] content) {
        final UUID fileId = randomUUID();
        final String blobName = storagePath.blobName(fileId);
        blobContainerClient.getBlobClient(blobName)
                .uploadWithResponse(
                        new BlobParallelUploadOptions(fromBytes(content))
                                .setMetadata(of("correlation_id", correlationId.toString(),
                                        "filename", filename)),
                        null, NONE);
        logger.info("Stored blob '{}' correlationId='{}' filename='{}'", blobName, correlationId, filename);
        return fileId;
    }
}
