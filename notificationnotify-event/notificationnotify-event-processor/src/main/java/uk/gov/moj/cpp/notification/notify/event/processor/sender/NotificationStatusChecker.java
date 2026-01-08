package uk.gov.moj.cpp.notification.notify.event.processor.sender;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.chomp;
import static uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus.fromStatus;

import uk.gov.moj.cpp.notification.notify.event.processor.client.GovNotifyClientProvider;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.NotificationResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.StatusResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExternalIdentifierJobState;
import uk.gov.service.notify.Notification;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;

import java.util.UUID;

import javax.inject.Inject;

import org.slf4j.Logger;

public class NotificationStatusChecker {

    @Inject
    private GovNotifyClientProvider govNotifyClientProvider;

    @Inject
    @SuppressWarnings("squid:S1312")
    private Logger logger;

    @SuppressWarnings({"squid:S2629", "squid:S2221"})
    public NotificationResponse checkStatus(final ExternalIdentifierJobState externalIdentifierJobState) {

        final UUID govNotifyNotificationId = externalIdentifierJobState.getTaskPayload().getExternalNotificationId();

        logger.info(format("Checking notification status of notification Id '%s'", govNotifyNotificationId));

        try {
            final NotificationClient notificationClient = govNotifyClientProvider.getClient();
            final Notification notification = notificationClient.getNotificationById(govNotifyNotificationId.toString());

            return new StatusResponse(fromStatus(notification.getStatus()));

        } catch (final NotificationClientException notificationClientException) {

            logger.error("An error was thrown while checking the email status", notificationClientException);

            final String message = format("Gov.Notify responded with '%s'", chomp(notificationClientException.getLocalizedMessage()));

            return new ErrorResponse(
                    message,
                    notificationClientException.getHttpResult());

        } catch (final Exception e) {

            logger.error("An unexpected error was thrown while checking the email status", e);

            final String message = format(
                    "Permanent failure, unexpected error while trying to deliver notification Id '%s', error message: '%s'",
                    externalIdentifierJobState.getNotificationId(),
                    e.getLocalizedMessage());

            return new ErrorResponse(message, 0);
        }
    }
}
