package uk.gov.moj.cpp.notification.notify.event.processor.task.processors;

import static java.lang.String.format;
import static uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus.DELIVERED;

import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus;
import uk.gov.moj.cpp.notification.notify.event.processor.response.StatusResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.task.Task;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.NotificationJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.CompleteHandler;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.RetryHandler;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.PermanentFailureHandler;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmailStatusResponseProcessor {

    @Inject
    private CompleteHandler completeHandler;

    @Inject
    private RetryHandler retryHandler;

    @Inject
    private PermanentFailureHandler permanentFailureHandler;

    private static final  Logger LOGGER = LoggerFactory.getLogger(EmailStatusResponseProcessor.class);

    public ExecutionInfo process(final NotificationJobState notificationJobState,
                                 final StatusResponse statusResponse,
                                 final Task task) {

        final NotificationStatus notificationStatus = statusResponse.getNotificationStatus();
            LOGGER.info("notificationStatus :: {}", notificationStatus);


        if (notificationStatus == DELIVERED) {
            return completeHandler.handle(notificationJobState, task);
        }

        if (notificationStatus.isInProgress()) {
            return retryHandler.handle(notificationJobState.getNotificationId(), task, notificationStatus);
        }

        return permanentFailureHandler.handle(
                notificationJobState.getNotificationId(),
                task,
                format("Gov.Notify responded with status '%s'", notificationStatus.getStatus()));
    }
}
