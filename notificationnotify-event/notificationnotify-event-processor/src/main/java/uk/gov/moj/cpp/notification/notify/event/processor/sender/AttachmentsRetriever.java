package uk.gov.moj.cpp.notification.notify.event.processor.sender;

import static java.lang.String.format;
import static uk.gov.moj.cpp.notification.notify.filestore.azure.StoragePath.internal;

import uk.gov.moj.cpp.notification.notify.event.processor.download.SuccessfulDocumentDownload;
import uk.gov.moj.cpp.notification.notify.event.processor.response.DownloadResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.NotificationResponse;
import uk.gov.moj.cpp.notification.notify.filestore.azure.StoragePath;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.models.BlobStorageException;

import org.apache.http.HttpStatus;
import org.slf4j.Logger;

@ApplicationScoped
public class AttachmentsRetriever {

    private static final StoragePath BLOB_PATH = internal();
    private static final long MAX_BLOB_SIZE_BYTES = 1_000_000_000L;

    @Inject
    @SuppressWarnings("squid:S1312")
    private Logger logger;

    @Inject
    private BlobContainerClient blobContainerClient;

    @SuppressWarnings("squid:S1166")
    public NotificationResponse getAttachment(final UUID notificationId, final UUID fileId) {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Looking up file '{}' for notificationId: {}", fileId, notificationId);
            }

            final String blobName = BLOB_PATH.blobName(fileId);
            final BlobClient blobClient = blobContainerClient.getBlobClient(blobName);

            if (!blobClient.exists()) {
                final String errorMessage = format("File attachment with id '%s' not found in File Service for notification: %s", fileId, notificationId);
                logger.error(errorMessage);
                return new ErrorResponse(errorMessage, HttpStatus.SC_NOT_FOUND);
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Successfully looked up file '{}' for notificationId: {}", fileId, notificationId);
            }

            final Map<String, String> metadata = blobClient.getProperties().getMetadata();
            final String filename = metadata.getOrDefault("filename", fileId.toString());

            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            blobClient.downloadStreamWithResponse(outputStream, new BlobRange(0, MAX_BLOB_SIZE_BYTES), null, null, false, null, null);
            final byte[] bytes = outputStream.toByteArray();

            return new DownloadResponse(new SuccessfulDocumentDownload(
                    HttpStatus.SC_OK, new ByteArrayInputStream(bytes), bytes.length, bytes, filename));
        } catch (final BlobStorageException e) {
            final String errorMessage = format("Failed to retrieve file attachment with id '%s' from File Service for notification: %s", fileId, notificationId);
            logger.error(errorMessage, e);
            return new ErrorResponse(errorMessage, 999);
        }
    }
}
