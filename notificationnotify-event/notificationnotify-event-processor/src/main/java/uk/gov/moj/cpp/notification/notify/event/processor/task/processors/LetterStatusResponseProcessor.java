package uk.gov.moj.cpp.notification.notify.event.processor.task.processors;

import static java.lang.String.format;
import static uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus.ACCEPTED;
import static uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus.RECEIVED;

import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus;
import uk.gov.moj.cpp.notification.notify.event.processor.response.StatusResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.task.Task;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExternalIdentifierJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.CompleteHandler;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.LetterAcceptedHandler;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.RetryHandler;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.InvalidRequestHandler;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.PermanentFailureHandler;

import javax.inject.Inject;

public class LetterStatusResponseProcessor {

    @Inject
    private CompleteHandler completeHandler;

    @Inject
    private RetryHandler retryHandler;

    @Inject
    private PermanentFailureHandler permanentFailureHandler;

    @Inject
    private InvalidRequestHandler invalidRequestHandler;

    @Inject
    private UtcClock utcClock;

    public ExecutionInfo process(final ExternalIdentifierJobState externalIdentifierJobState,
                                 final StatusResponse statusResponse,
                                 final Task task,
                                 final LetterAcceptedHandler letterAcceptedHandler) {

        final NotificationStatus notificationStatus = statusResponse.getNotificationStatus();

        if (notificationStatus == RECEIVED) {
            return completeHandler.handle(externalIdentifierJobState, task);
        }

        if (statusResponse.isSuccessful() && statusResponse.getNotificationStatus().isInvalidRequest()) {
            final String errorMessage = format("Validation failed for '%s'", externalIdentifierJobState.getNotificationId());


            return invalidRequestHandler.handle(externalIdentifierJobState, errorMessage, utcClock.now());
        }

        if (notificationStatus == ACCEPTED) {
            return letterAcceptedHandler.handle(externalIdentifierJobState);
        }

        if (notificationStatus.isInProgress()) {
            return retryHandler.handle(externalIdentifierJobState.getNotificationId(), task, notificationStatus);
        }

        return permanentFailureHandler.handle(
                externalIdentifierJobState.getNotificationId(),
                task,
                format("Gov.Notify responded with status '%s'", notificationStatus.getStatus()));
    }
}
