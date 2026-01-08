package uk.gov.moj.cpp.notification.notify.event.processor.task.processors;

import org.slf4j.Logger;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.NotificationNotifyCommandSender;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.task.Task;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.NotificationJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.PermanentFailureHandler;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.RetryHandler;

import javax.inject.Inject;

import static java.lang.String.format;

public class SenderErrorResponseProcessor {

    @Inject
    private NotificationNotifyCommandSender notificationNotifyCommandSender;

    @Inject
    private PermanentFailureHandler permanentFailureHandler;

    @Inject
    private FailureSelector failureSelector;

    @Inject
    private RetryHandler retryHandler;

    @SuppressWarnings({"squid:S1312"})//suppressing Sonar warning of logger not being static final
    @Inject
    private Logger logger;


    public ExecutionInfo process(final NotificationJobState notificationJobState,
                                 final ErrorResponse errorResponse,
                                 final Task task) {

        notificationNotifyCommandSender.markAsAttempted(
                notificationJobState.getNotificationId(),
                errorResponse.getErrorMessage(),
                errorResponse.getStatusCode());

        if (failureSelector.isTemporaryFailure(errorResponse)) {
            logger.info(format("Temporary failure for '%s' for notification job '%s'", task.getTaskName(), notificationJobState.getNotificationId()));
            return retryHandler.handle(notificationJobState.getNotificationId(),task, errorResponse);
        }

        return permanentFailureHandler.handle(
                notificationJobState.getNotificationId(),
                errorResponse,
                task);
    }
}
