package uk.gov.moj.cpp.notification.notify.event.processor.task.processors;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.SEND_EMAIL;

import java.util.UUID;
import org.slf4j.Logger;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.NotificationNotifyCommandSender;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.NotificationJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.PermanentFailureHandler;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.RetryHandler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SenderErrorResponseProcessorTest {

    @Mock
    private NotificationNotifyCommandSender notificationNotifyCommandSender;

    @Mock
    private PermanentFailureHandler permanentFailureHandler;

    @Mock
    private FailureSelector failureSelector;

    @Mock
    private RetryHandler retryHandler;

    @Mock
    private Logger logger;

    @InjectMocks
    private SenderErrorResponseProcessor senderErrorResponseProcessor;

    @Test
    public void shouldProcessTemporaryFailure() {
        final UUID notificationId = UUID.randomUUID();
        final NotificationJobState notificationJobState = mock(NotificationJobState.class);
        final ErrorResponse errorResponse = mock(ErrorResponse.class);
        final String errorMessage = "Error message";
        final int statusCode = SC_NOT_FOUND;
        final ExecutionInfo expectedExecutionInfo = mock(ExecutionInfo.class);

        when(errorResponse.getErrorMessage()).thenReturn(errorMessage);
        when(errorResponse.getStatusCode()).thenReturn(statusCode);
        when(failureSelector.isTemporaryFailure(errorResponse)).thenReturn(true);
        when(notificationJobState.getNotificationId()).thenReturn(notificationId);
        when(retryHandler.handle(
                notificationJobState.getNotificationId(),
                SEND_EMAIL, errorResponse)).thenReturn(expectedExecutionInfo);

        final ExecutionInfo actualExecutionInfo = senderErrorResponseProcessor.process(
                notificationJobState,
                errorResponse,
                SEND_EMAIL);

        assertThat(actualExecutionInfo, is(expectedExecutionInfo));

        verify(notificationNotifyCommandSender).markAsAttempted(
                notificationJobState.getNotificationId(),
                errorMessage,
                statusCode);

        verify(retryHandler).handle(
                notificationJobState.getNotificationId(),
                SEND_EMAIL, errorResponse);

        verify(logger).info(String.format("Temporary failure for 'send-email' for notification job '%s'", notificationId));
    }

    @Test
    public void shouldProcessPermanentFailure() {
        final UUID notificationId = UUID.randomUUID();
        final NotificationJobState notificationJobState = mock(NotificationJobState.class);
        final ErrorResponse errorResponse = mock(ErrorResponse.class);
        final String errorMessage = "Error message";
        final int statusCode = SC_BAD_REQUEST;
        final ExecutionInfo expectedExecutionInfo = mock(ExecutionInfo.class);

        when(errorResponse.getErrorMessage()).thenReturn(errorMessage);
        when(errorResponse.getStatusCode()).thenReturn(statusCode);
        when(failureSelector.isTemporaryFailure(errorResponse)).thenReturn(false);
        when(notificationJobState.getNotificationId()).thenReturn(notificationId);
        when(permanentFailureHandler.handle(
                notificationId,
                errorResponse,
                SEND_EMAIL)).thenReturn(expectedExecutionInfo);

        final ExecutionInfo actualExecutionInfo = senderErrorResponseProcessor.process(
                notificationJobState,
                errorResponse,
                SEND_EMAIL);

        assertThat(actualExecutionInfo, is(expectedExecutionInfo));

        verify(notificationNotifyCommandSender).markAsAttempted(
                notificationId,
                errorMessage,
                statusCode);

        verify(permanentFailureHandler).handle(
                notificationId,
                errorResponse,
                SEND_EMAIL);
    }
}