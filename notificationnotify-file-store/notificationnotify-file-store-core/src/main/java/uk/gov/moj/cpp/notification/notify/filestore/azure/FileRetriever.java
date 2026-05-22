package uk.gov.moj.cpp.notification.notify.filestore.azure;

import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobStorageException;

import org.slf4j.Logger;

@ApplicationScoped
public class FileRetriever {

    private static final int HTTP_NOT_FOUND = 404;

    @Inject
    @SuppressWarnings("squid:S1312")
    private Logger logger;

    @Inject
    private BlobContainerClient blobContainerClient;

    public Optional<InputStream> retrieve(final StoragePath storagePath, final UUID fileId) {
        final String blobName = storagePath.blobName(fileId);
        final BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
        try {
            return Optional.of(blobClient.openInputStream());
        } catch (final BlobStorageException e) {
            if (e.getStatusCode() == HTTP_NOT_FOUND) {
                logger.info("Blob not found blobName='{}' fileId='{}'", blobName, fileId);
                return Optional.empty();
            }
            throw e;
        }
    }
}
