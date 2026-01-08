package uk.gov.moj.cpp.notification.notify.event.processor.task.handlers;

import static java.lang.String.format;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo.executionInfo;

import java.util.UUID;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.retry.RetryService;
import uk.gov.moj.cpp.notification.notify.event.processor.task.Task;

import javax.inject.Inject;

public class RetryHandler {
    
    @Inject
    private PermanentFailureHandler permanentFailureHandler;

    @Inject
    private RetryService retryService;

    public ExecutionInfo handle(final UUID notificationId,
                                final Task failedTaskForRetry,
                                final ErrorResponse errorResponse) {

        final ExecutionInfo exhaustTask = permanentFailureHandler.handle(notificationId,
                errorResponse, failedTaskForRetry);

        return executionInfo()
                .from(exhaustTask)
                .withShouldRetry(true)
                .build();
    }

    public ExecutionInfo handle(final UUID notificationId,
                                final Task failedTaskForRetry,
                                final NotificationStatus notificationStatus) {

        final int noOfConfiguredRetryAttempts = retryService.noOfOfConfiguredRetryAttempts(failedTaskForRetry);
        final String errorMessage = format(
                "Check delivery status failed after %d attempts. Permanent failure. Gov.Notify responded with status '%s'",
                noOfConfiguredRetryAttempts, notificationStatus.getStatus());

        final ExecutionInfo exhaustTask = permanentFailureHandler.handle(notificationId,
                failedTaskForRetry, errorMessage);

        return executionInfo()
                .from(exhaustTask)
                .withShouldRetry(true)
                .build();
    }
}
