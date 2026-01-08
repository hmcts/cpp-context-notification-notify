package uk.gov.moj.cpp.notification.notify.event.processor.sender;

import static java.lang.String.format;
import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.commons.lang3.StringUtils.chomp;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import uk.gov.moj.cpp.notification.notify.event.processor.client.GovNotifyClientProvider;
import uk.gov.moj.cpp.notification.notify.event.processor.download.DocumentDownloadClient;
import uk.gov.moj.cpp.notification.notify.event.processor.download.DocumentDownloadResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.download.SuccessfulDocumentDownload;
import uk.gov.moj.cpp.notification.notify.event.processor.download.UnsuccessfulDocumentDownload;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.NotificationResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.SenderResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.NotificationJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.SendLetterDetails;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.SendLetterDetailsJobState;
import uk.gov.service.notify.LetterResponse;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import javax.inject.Inject;

import org.slf4j.Logger;

public class LetterSender implements NotificationSender {

    @Inject
    private GovNotifyClientProvider govNotifyClientProvider;

    @Inject
    private DocumentDownloadClient documentDownloadClient;

    @Inject
    @SuppressWarnings("squid:S1312")
    private Logger logger;

    private static final String FIRST_CLASS_POSTAGE = "first";


    @SuppressWarnings({"squid:S2629"})
    @Override
    public NotificationResponse send(final NotificationJobState<?> notificationJobState) {

        final SendLetterDetailsJobState sendLetterDetailsJobState = (SendLetterDetailsJobState) notificationJobState;

        final UUID notificationId = sendLetterDetailsJobState.getNotificationId();

        logger.info(format("Sending letter with notification Id '%s'", notificationId));

        final SendLetterDetails sendLetterDetails = sendLetterDetailsJobState.getTaskPayload();
        final String documentUrl = sendLetterDetails.getDocumentUrl();
        final String postage = sendLetterDetails.getPostage();

        logger.info("LetterSender Postage  value :: {}", postage);

        final DocumentDownloadResponse documentDownloadResponse = downloadResponse(documentUrl);
        if (documentDownloadResponse != null) {

            if (documentDownloadResponse.downloadSuccessful()) {
                final SuccessfulDocumentDownload successfulDocumentDownload = (SuccessfulDocumentDownload) documentDownloadResponse;
                final int contentSize = successfulDocumentDownload.contentSize();

                logger.info("Download successful for Notification:{}, with download content size of {}", notificationId, contentSize);
                return sendToGovNotify(notificationId, successfulDocumentDownload, postage);
            }
            logger.info("Download failed for Notification: {}", notificationId);
            return createDownloadError((UnsuccessfulDocumentDownload) documentDownloadResponse);
        } else {

            final String errorMessage = String.format("Document download failed for Notification: %s", notificationId);
            return new ErrorResponse(errorMessage, 0);
        }
    }

    private DocumentDownloadResponse downloadResponse(final String documentUrl) {

        DocumentDownloadResponse documentDownloadResponse = null;
        try {
            documentDownloadResponse = documentDownloadClient.getDocument(documentUrl);
        } catch (IOException e) {
            logger.error("Download failed with exception {}", e.getMessage(), e);
        }
        return documentDownloadResponse;
    }

    @SuppressWarnings({"squid:S2221"})
    private NotificationResponse sendToGovNotify(final UUID notificationId, final SuccessfulDocumentDownload successfulDocumentDownload, final String postage) {
        final NotificationClient notificationClient = govNotifyClientProvider.getClient();
        LetterResponse letterResponse;
        final InputStream letterInputStream = successfulDocumentDownload.getContent();
        try {
            if(isNotBlank(postage) && postage.equals(FIRST_CLASS_POSTAGE)) {
                letterResponse = notificationClient.sendPrecompiledLetterWithInputStream(
                        notificationId.toString(),
                        letterInputStream, postage);
            } else {
                letterResponse = notificationClient.sendPrecompiledLetterWithInputStream(
                        notificationId.toString(),
                        letterInputStream);
            }

            return new SenderResponse(letterResponse.getNotificationId(), null);

        } catch (final NotificationClientException notificationClientException) {

            logger.error("An error was thrown while sending email", notificationClientException);

            return new ErrorResponse(
                    format("Gov.Notify responded with '%s'", chomp(notificationClientException.getLocalizedMessage())),
                    notificationClientException.getHttpResult());
        } catch (final Exception e) {

            logger.error("An unexpected error was thrown while sending letter", e);

            final String message = format(
                    "Permanent failure, unexpected error while trying to deliver notification Id '%s', error message: '%s'",
                    notificationId,
                    e.getLocalizedMessage());

            return new ErrorResponse(message, 0);
        } finally {
            closeQuietly(letterInputStream);
        }
    }

    private NotificationResponse createDownloadError(final UnsuccessfulDocumentDownload unsuccessfulDocumentDownload) {

        final String responseBody = unsuccessfulDocumentDownload.getResponseBody();
        final int httpResult = unsuccessfulDocumentDownload.getHttpResult();

        final String errorMessage = String.format("Failed to download pdf. Error message: '%s'", responseBody);

        return new ErrorResponse(errorMessage, httpResult);
    }
}
