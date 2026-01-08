package uk.gov.moj.cpp.notification.notify.event.processor.task.handlers;

import java.time.ZonedDateTime;
import java.util.UUID;
import javax.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.jobstore.persistence.Priority;
import uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.retry.RetryService;
import uk.gov.moj.cpp.notification.notify.event.processor.task.Task;

import static java.time.ZonedDateTime.now;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.INPROGRESS;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_EMAIL_STATUS;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.NOTIFICATION_FAILED;


@ExtendWith(MockitoExtension.class)
public class RetryHandlerTest {

    @Mock
    private PermanentFailureHandler permanentFailureHandler;

    @Mock
    private RetryService retryService;

    @InjectMocks
    private RetryHandler retryHandler;

    @Test
    public void shouldReturnExecutionInfoWithRetryAndExhaustTaskDetailsPopulatedGivenErrorResponse() {

        final UUID notificationId = randomUUID();
        final ZonedDateTime now = now();
        final ErrorResponse errorResponse = new ErrorResponse("error message", 21);
        final ExecutionInfo permanentFailureTaskExecutionInfo = new ExecutionInfo(
                mock(JsonObject.class),
                NOTIFICATION_FAILED.getTaskName(),
                now,
                INPROGRESS,
                Priority.MEDIUM
        );
        when(permanentFailureHandler.handle(notificationId, errorResponse, CHECK_EMAIL_STATUS))
                .thenReturn(permanentFailureTaskExecutionInfo);

        final ExecutionInfo result = retryHandler.handle(notificationId, CHECK_EMAIL_STATUS, errorResponse);

        assertThat(result.isShouldRetry(), is(true));
        assertThat(result.getNextTask(), is(permanentFailureTaskExecutionInfo.getNextTask()));
        assertThat(result.getJobData(), is(permanentFailureTaskExecutionInfo.getJobData()));
        assertThat(result.getExecutionStatus(), is(INPROGRESS));
        assertThat(result.getNextTaskStartTime(), is(now));
    }

    @Test
    public void shouldReturnExecutionInfoWithRetryAndExhaustTaskDetailsPopulatedGivenErrorMessage() {

        final UUID notificationId = randomUUID();
        final ZonedDateTime now = now();
        final ExecutionInfo permanentFailureTaskExecutionInfo = new ExecutionInfo(
                mock(JsonObject.class),
                NOTIFICATION_FAILED.getTaskName(),
                now,
                INPROGRESS,
                Priority.MEDIUM
        );
        when(permanentFailureHandler.handle(any(UUID.class), any(Task.class), any(String.class)))
                .thenReturn(permanentFailureTaskExecutionInfo);
        when(retryService.noOfOfConfiguredRetryAttempts(CHECK_EMAIL_STATUS)).thenReturn(2);

        final ExecutionInfo result = retryHandler.handle(notificationId, CHECK_EMAIL_STATUS, NotificationStatus.INVALID_REQUEST);

        assertThat(result.isShouldRetry(), is(true));
        assertThat(result.getNextTask(), is(permanentFailureTaskExecutionInfo.getNextTask()));
        assertThat(result.getJobData(), is(permanentFailureTaskExecutionInfo.getJobData()));
        assertThat(result.getExecutionStatus(), is(INPROGRESS));
        assertThat(result.getNextTaskStartTime(), is(now));
        verify(permanentFailureHandler).handle(notificationId, CHECK_EMAIL_STATUS,
                "Check delivery status failed after 2 attempts. Permanent failure. Gov.Notify responded with status 'validation-failed'");
    }
}
