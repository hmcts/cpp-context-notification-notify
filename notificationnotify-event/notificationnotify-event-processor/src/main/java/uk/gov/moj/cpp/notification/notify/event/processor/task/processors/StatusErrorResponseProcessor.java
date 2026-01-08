package uk.gov.moj.cpp.notification.notify.event.processor.task.processors;

import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.task.Task;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExternalIdentifierJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.RetryHandler;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.PermanentFailureHandler;

import javax.inject.Inject;

public class StatusErrorResponseProcessor {

    @Inject
    private RetryHandler retryHandler;

    @Inject
    private PermanentFailureHandler permanentFailureHandler;

    @Inject
    private FailureSelector failureSelector;

    public ExecutionInfo process(final ExternalIdentifierJobState externalIdentifierJobState,
                                 final ErrorResponse errorResponse,
                                 final Task task) {

        if (failureSelector.isTemporaryFailure(errorResponse)) {
            return retryHandler.handle(externalIdentifierJobState.getNotificationId(), task, errorResponse);
        }

        return permanentFailureHandler.handle(
                externalIdentifierJobState.getNotificationId(),
                errorResponse,
                task);
    }
}
