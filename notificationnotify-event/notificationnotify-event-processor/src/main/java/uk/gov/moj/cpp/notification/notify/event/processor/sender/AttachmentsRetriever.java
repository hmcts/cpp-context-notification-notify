package uk.gov.moj.cpp.notification.notify.event.processor.sender;


import static com.google.common.io.ByteStreams.toByteArray;
import static java.lang.String.format;

import uk.gov.justice.services.fileservice.api.FileRetriever;
import uk.gov.justice.services.fileservice.api.FileServiceException;
import uk.gov.justice.services.fileservice.domain.FileReference;
import uk.gov.moj.cpp.notification.notify.event.processor.download.SuccessfulDocumentDownload;
import uk.gov.moj.cpp.notification.notify.event.processor.response.DownloadResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.NotificationResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.http.HttpStatus;
import org.slf4j.Logger;

@ApplicationScoped
public class AttachmentsRetriever {

    @Inject
    @SuppressWarnings("squid:S1312")
    private Logger logger;

    @Inject
    private FileRetriever fileRetriever;

    @SuppressWarnings("squid:S1166")
    //squid:S1166 - IOException is turned into ErrorResponseMessage, no need to rethrow
    public NotificationResponse getAttachment(final UUID notificationId, final UUID fileId) {

        try {
            if (logger.isDebugEnabled()) {
                logger.debug(format("Looking up file '%s' for notificationId: %s", fileId, notificationId));
            }
            final Optional<FileReference> fileReference = fileRetriever.retrieve(fileId);

            if (fileReference.isPresent()) {
                return buildSuccessfulResponse(notificationId, fileId, fileReference.get());
            } else {
                final String errorMessage = format("File attachment with id '%s' not found in File Service for notification: %s", fileId, notificationId);
                logger.error(errorMessage);
                return new ErrorResponse(errorMessage, HttpStatus.SC_NOT_FOUND);
            }
        } catch (FileServiceException | IOException e) {
            final String errorMessage = format("Failed to retrieve file attachment with id '%s' from File Service for notification: %s", fileId, notificationId);
            logger.error(errorMessage, e);
            return new ErrorResponse(errorMessage, 999);
        }
    }

    private NotificationResponse buildSuccessfulResponse(final UUID notificationId, final UUID fileId, final FileReference fileReference) throws IOException {
        if (logger.isDebugEnabled()) {
            logger.debug(format("Successfully looked up file '%s' for notificationId: %s", fileId, notificationId));
        }

        try (final InputStream inputStream = fileReference.getContentStream()) {
            final byte[] bytes = toByteArray(inputStream);

            return new DownloadResponse(new SuccessfulDocumentDownload(HttpStatus.SC_OK, new ByteArrayInputStream(bytes), bytes.length, bytes, fileReference.getMetadata().getString("fileName")));
        }
    }
}
