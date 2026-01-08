package uk.gov.moj.cpp.notification.notify.event.processor.task.processors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus.CREATED;
import static uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus.DELIVERED;
import static uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus.FAILED;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_EMAIL_STATUS;

import java.util.UUID;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus;
import uk.gov.moj.cpp.notification.notify.event.processor.response.StatusResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.task.Task;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExternalIdentifierJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.CompleteHandler;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.RetryHandler;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.PermanentFailureHandler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EmailStatusResponseProcessorTest {

    @Mock
    private CompleteHandler completeHandler;

    @Mock
    private RetryHandler retryHandler;

    @Mock
    private PermanentFailureHandler permanentFailureHandler;

    @InjectMocks
    private EmailStatusResponseProcessor emailStatusResponseProcessor;

    @Test
    public void shouldProcessDeliveredStatus() {

        final ExternalIdentifierJobState externalIdentifierJobState = mock(ExternalIdentifierJobState.class);
        final StatusResponse statusResponse = mock(StatusResponse.class);
        final ExecutionInfo expectedExecutionInfo = mock(ExecutionInfo.class);

        when(statusResponse.getNotificationStatus()).thenReturn(DELIVERED);
        when(completeHandler.handle(externalIdentifierJobState, CHECK_EMAIL_STATUS)).thenReturn(expectedExecutionInfo);

        final ExecutionInfo actualExecutionInfo = emailStatusResponseProcessor.process(
                externalIdentifierJobState,
                statusResponse,
                CHECK_EMAIL_STATUS);

        assertThat(actualExecutionInfo, is(expectedExecutionInfo));

        verify(completeHandler).handle(externalIdentifierJobState, CHECK_EMAIL_STATUS);
    }

    @Test
    public void shouldProcessInProgressStatus() {

        final UUID notificationId = UUID.randomUUID();
        final ExternalIdentifierJobState externalIdentifierJobState = mock(ExternalIdentifierJobState.class);
        final StatusResponse statusResponse = mock(StatusResponse.class);
        final ExecutionInfo expectedExecutionInfo = mock(ExecutionInfo.class);

        when(statusResponse.getNotificationStatus()).thenReturn(CREATED);
        when(retryHandler.handle(any(UUID.class), any(Task.class), any(NotificationStatus.class))).thenReturn(expectedExecutionInfo);
        when(externalIdentifierJobState.getNotificationId()).thenReturn(notificationId);

        final ExecutionInfo actualExecutionInfo = emailStatusResponseProcessor.process(
                externalIdentifierJobState,
                statusResponse,
                CHECK_EMAIL_STATUS);

        assertThat(actualExecutionInfo, is(expectedExecutionInfo));

        verify(retryHandler).handle(notificationId, CHECK_EMAIL_STATUS, CREATED);
    }

    @Test
    public void shouldProcessPermanentFailureDueToFailureStatus() {

        final UUID notificationId = UUID.randomUUID();
        final ExternalIdentifierJobState externalIdentifierJobState = mock(ExternalIdentifierJobState.class);
        final StatusResponse statusResponse = mock(StatusResponse.class);
        final ExecutionInfo expectedExecutionInfo = mock(ExecutionInfo.class);
        final String expectedMessage = "Gov.Notify responded with status 'technical-failure'";

        when(statusResponse.getNotificationStatus()).thenReturn(FAILED);
        when(externalIdentifierJobState.getNotificationId()).thenReturn(notificationId);
        when(permanentFailureHandler.handle(
                notificationId,
                CHECK_EMAIL_STATUS,
                expectedMessage)).thenReturn(expectedExecutionInfo);

        final ExecutionInfo actualExecutionInfo = emailStatusResponseProcessor.process(
                externalIdentifierJobState,
                statusResponse,
                CHECK_EMAIL_STATUS);

        assertThat(actualExecutionInfo, is(expectedExecutionInfo));

        verify(permanentFailureHandler).handle(
                notificationId,
                CHECK_EMAIL_STATUS,
                expectedMessage);
    }
}