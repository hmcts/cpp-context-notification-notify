package uk.gov.moj.cpp.notification.notify.filestore.azure;

import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobRange;

import org.slf4j.Logger;

/**
 * CDI bean that retrieves blobs from the service's own Azure Blob container using the
 * {@link StoragePath} path-prefix convention: {@code {prefix}/{fileId}}.
 */
@ApplicationScoped
public class FileRetriever {

    private static final long MAX_BLOB_SIZE_BYTES = 1_000_000_000L;

    @Inject
    @SuppressWarnings("squid:S1312")
    private Logger logger;

    @Inject
    private BlobContainerClient blobContainerClient;

    /**
     * Downloads the blob at {@code storagePath/{fileId}}.
     *
     * @param storagePath path-prefix for the blob (e.g. {@link StoragePath#internal()})
     * @param fileId      the UUID returned by {@link FileStorer#store} when the blob was uploaded
     * @return the raw bytes, or {@link Optional#empty()} if the blob does not exist
     */
    public Optional<byte[]> retrieve(final StoragePath storagePath, final UUID fileId) {
        final String blobName = storagePath.blobName(fileId);
        final BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
        if (!blobClient.exists()) {
            logger.info("Blob not found blobName='{}' fileId='{}'", blobName, fileId);
            return Optional.empty();
        }
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        blobClient.downloadStreamWithResponse(outputStream, new BlobRange(0, MAX_BLOB_SIZE_BYTES),
                null, null, false, null, null);
        return Optional.of(outputStream.toByteArray());
    }
}
