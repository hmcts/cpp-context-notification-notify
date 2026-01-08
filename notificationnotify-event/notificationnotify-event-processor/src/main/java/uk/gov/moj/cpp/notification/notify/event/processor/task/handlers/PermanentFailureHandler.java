package uk.gov.moj.cpp.notification.notify.event.processor.task.handlers;

import java.util.Optional;
import java.util.UUID;
import javax.inject.Inject;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.task.Task;
import uk.gov.moj.cpp.notification.notify.event.processor.task.processors.ErrorMessageFactory;

import static java.util.Optional.empty;
import static java.util.Optional.of;

public class PermanentFailureHandler {

    @Inject
    private ErrorMessageFactory errorMessageFactory;

    @Inject
    private NotificationFailedTaskFactory notificationFailedTaskFactory;

    public ExecutionInfo handle(
            final UUID notificationId,
            final ErrorResponse errorResponse,
            final Task failedTask) {

        final String errorMessage = errorMessageFactory.createErrorMessage(
                notificationId,
                errorResponse,
                failedTask);

        return handle(
                notificationId,
                failedTask,
                errorMessage,
                of(errorResponse.getStatusCode()));
    }

    public ExecutionInfo handle(final UUID notificationId,
                                final Task failedTask,
                                final String errorMessage) {

        return handle(notificationId, failedTask, errorMessage, empty());
    }

    private ExecutionInfo handle(final UUID notificationId,
                                final Task failedTask,
                                final String errorMessage,
                                final Optional<Integer> statusCode) {

        return notificationFailedTaskFactory.create(notificationId, failedTask, errorMessage, statusCode);
   }
}
