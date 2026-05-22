package uk.gov.moj.cpp.notification.notify.filestore.azure;

import static com.azure.core.util.BinaryData.fromStream;
import static com.azure.core.util.Context.NONE;
import static java.util.Map.of;
import static java.util.UUID.randomUUID;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.options.BlobParallelUploadOptions;

import java.io.InputStream;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

@ApplicationScoped
public class FileStorer {

    @Inject
    @SuppressWarnings("squid:S1312")
    private Logger logger;

    @Inject
    private BlobContainerClient blobContainerClient;

    @Inject
    private AzureBlobConfiguration azureBlobConfiguration;

    public UUID store(final StoragePath storagePath,
                      final UUID correlationId,
                      final String filename,
                      final InputStream content) {
        final UUID fileId = randomUUID();
        final String blobName = storagePath.blobName(fileId);
        blobContainerClient.getBlobClient(blobName)
                .uploadWithResponse(
                        new BlobParallelUploadOptions(fromStream(content))
                                .setMetadata(of("correlation_id", correlationId.toString(),
                                        "filename", filename)),
                        azureBlobConfiguration.getTransferTimeout(), NONE);
        logger.info("Stored blob '{}' correlationId='{}' filename='{}'", blobName, correlationId, filename);
        return fileId;
    }
}
