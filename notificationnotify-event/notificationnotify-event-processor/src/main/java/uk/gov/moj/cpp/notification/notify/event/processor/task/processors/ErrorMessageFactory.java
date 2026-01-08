package uk.gov.moj.cpp.notification.notify.event.processor.task.processors;

import static java.lang.String.format;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import javax.inject.Inject;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.retry.RetryService;
import uk.gov.moj.cpp.notification.notify.event.processor.task.Task;

import java.util.UUID;

public class ErrorMessageFactory {

    @Inject
    private RetryService retryService;

    public String createErrorMessage(
            final UUID notificationId,
            final ErrorResponse errorResponse,
            final Task task) {
        return format("%s %s", getBaseMessage(notificationId, errorResponse, task), errorResponse.getErrorMessage());
    }

    private String getBaseMessage(final UUID notificationId,
                                  final ErrorResponse errorResponse,
                                  final Task task) {
        final String taskName = task.getTaskName();

        if (errorResponse.getStatusCode() == SC_BAD_REQUEST) {
            return format("Permanent failure while trying to %s for notification job %s. Bad Request (400).", taskName, notificationId);
        }

        final int noOfConfiguredRetryAttempts = retryService.noOfOfConfiguredRetryAttempts(task);

        return format("Failed to %s after %d attempts.", taskName, noOfConfiguredRetryAttempts);
    }
}
