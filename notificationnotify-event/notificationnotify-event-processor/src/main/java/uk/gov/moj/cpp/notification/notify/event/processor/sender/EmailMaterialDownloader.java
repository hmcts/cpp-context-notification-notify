package uk.gov.moj.cpp.notification.notify.event.processor.sender;


import static java.lang.String.format;

import uk.gov.moj.cpp.notification.notify.event.processor.download.DocumentDownloadClient;
import uk.gov.moj.cpp.notification.notify.event.processor.download.DocumentDownloadResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.download.SuccessfulDocumentDownload;
import uk.gov.moj.cpp.notification.notify.event.processor.download.UnsuccessfulDocumentDownload;
import uk.gov.moj.cpp.notification.notify.event.processor.response.DownloadResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.NotificationResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.SendEmailDetails;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;

import org.slf4j.Logger;

public class EmailMaterialDownloader {

    @Inject
    @SuppressWarnings("squid:S1312")
    private Logger logger;

    @Inject
    private DocumentDownloadClient documentDownloadClient;

    @SuppressWarnings("squid:S1166") //squid:S1166 - IOException is turned into ErrorResponseMessage, no need to rethrow
    public NotificationResponse downloadMaterial(final UUID notificationId,
                                                 final SendEmailDetails sendEmailDetails) {

        final Optional<String> materialUrl = sendEmailDetails.getMaterialUrl();
        try {
            final DocumentDownloadResponse documentDownloadResponse = documentDownloadClient.getDocument(materialUrl.orElse(""));
            logger.info("documentDownloadResponse with HTTP result :{}, download successful :: {}  ", documentDownloadResponse.getHttpResult(), documentDownloadResponse.downloadSuccessful());
            if (documentDownloadResponse.downloadSuccessful()) {
                final SuccessfulDocumentDownload successfulDocumentDownload = (SuccessfulDocumentDownload) documentDownloadResponse;
                final int contentSize = successfulDocumentDownload.contentSize();

                if(logger.isInfoEnabled()) {
                    logger.info("Email Download successful for Notification:{}, with download content size of {}", notificationId, contentSize);
                }
                logger.info("EmailMaterialDownloader file Name{}", successfulDocumentDownload.getFileName());

                return new DownloadResponse(successfulDocumentDownload);
            }

            if(logger.isInfoEnabled()) {
                logger.info(format("Email attachment Download failed for Notification: %s", notificationId));
            }

            return createDownloadError((UnsuccessfulDocumentDownload) documentDownloadResponse);
        } catch (final IOException e) {
            final String errorMessage = format("Email attachment download failed for Notification: %s", notificationId);
            return new ErrorResponse(errorMessage, 999);
        }
    }

    private NotificationResponse createDownloadError(final UnsuccessfulDocumentDownload unsuccessfulDocumentDownload) {

        final String responseBody = unsuccessfulDocumentDownload.getResponseBody();
        final int httpResult = unsuccessfulDocumentDownload.getHttpResult();

        final String errorMessage = format("Failed to download. Error message: '%s'", responseBody);

        return new ErrorResponse(errorMessage, httpResult);
    }
}
