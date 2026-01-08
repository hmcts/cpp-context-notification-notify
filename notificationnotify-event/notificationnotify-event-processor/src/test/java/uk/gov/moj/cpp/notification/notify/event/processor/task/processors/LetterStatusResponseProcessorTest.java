package uk.gov.moj.cpp.notification.notify.event.processor.task.processors;

import static java.lang.String.format;
import static java.time.ZonedDateTime.now;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus.ACCEPTED;
import static uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus.FAILED;
import static uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus.INVALID_REQUEST;
import static uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus.PENDING_VIRUS_CHECK;
import static uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus.RECEIVED;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_LETTER_STATUS;

import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus;
import uk.gov.moj.cpp.notification.notify.event.processor.response.StatusResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.task.Task;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExternalIdentifierJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.CompleteHandler;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.RetryHandler;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.InvalidRequestHandler;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.LetterAcceptedNextTaskExecutionHandler;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.PermanentFailureHandler;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class LetterStatusResponseProcessorTest {

    @Mock
    private CompleteHandler completeHandler;

    @Mock
    private RetryHandler retryHandler;

    @Mock
    private PermanentFailureHandler permanentFailureHandler;

    @Mock
    private InvalidRequestHandler invalidRequestHandler;

    @Mock
    private UtcClock utcClock;

    @InjectMocks
    private LetterStatusResponseProcessor letterStatusResponseProcessor;

    @Test
    public void shouldProcessReceivedStatus() {

        final ExternalIdentifierJobState externalIdentifierJobState = mock(ExternalIdentifierJobState.class);
        final StatusResponse statusResponse = mock(StatusResponse.class);
        final ExecutionInfo expectedExecutionInfo = mock(ExecutionInfo.class);
        final LetterAcceptedNextTaskExecutionHandler letterAcceptedNextTaskExecutionHandler = mock(LetterAcceptedNextTaskExecutionHandler.class);

        when(statusResponse.getNotificationStatus()).thenReturn(RECEIVED);
        when(completeHandler.handle(externalIdentifierJobState, CHECK_LETTER_STATUS)).thenReturn(expectedExecutionInfo);

        final ExecutionInfo actualExecutionInfo = letterStatusResponseProcessor.process(externalIdentifierJobState, statusResponse, CHECK_LETTER_STATUS, letterAcceptedNextTaskExecutionHandler);

        assertThat(actualExecutionInfo, is(expectedExecutionInfo));

        verify(completeHandler).handle(externalIdentifierJobState, CHECK_LETTER_STATUS);
    }

    @Test
    public void shouldProcessAcceptedStatus() {

        final ExternalIdentifierJobState externalIdentifierJobState = mock(ExternalIdentifierJobState.class);
        final StatusResponse statusResponse = mock(StatusResponse.class);
        final ExecutionInfo expectedExecutionInfo = mock(ExecutionInfo.class);
        final LetterAcceptedNextTaskExecutionHandler letterAcceptedNextTaskExecutionHandler = mock(LetterAcceptedNextTaskExecutionHandler.class);

        when(statusResponse.getNotificationStatus()).thenReturn(ACCEPTED);
        when(letterAcceptedNextTaskExecutionHandler.handle(externalIdentifierJobState)).thenReturn(expectedExecutionInfo);

        final ExecutionInfo actualExecutionInfo = letterStatusResponseProcessor.process(externalIdentifierJobState, statusResponse, CHECK_LETTER_STATUS, letterAcceptedNextTaskExecutionHandler);

        assertThat(actualExecutionInfo, is(expectedExecutionInfo));

        verify(letterAcceptedNextTaskExecutionHandler).handle(externalIdentifierJobState);
    }

    @Test
    public void shouldProcessInProgressStatus() {

        final UUID notificationId = UUID.randomUUID();
        final ExternalIdentifierJobState externalIdentifierJobState = mock(ExternalIdentifierJobState.class);
        final StatusResponse statusResponse = mock(StatusResponse.class);
        final ExecutionInfo expectedExecutionInfo = mock(ExecutionInfo.class);
        final LetterAcceptedNextTaskExecutionHandler letterAcceptedNextTaskExecutionHandler = mock(LetterAcceptedNextTaskExecutionHandler.class);

        when(statusResponse.getNotificationStatus()).thenReturn(PENDING_VIRUS_CHECK);
        when(retryHandler.handle(any(UUID.class), any(Task.class), any(NotificationStatus.class))).thenReturn(expectedExecutionInfo);
        when(externalIdentifierJobState.getNotificationId()).thenReturn(notificationId);

        final ExecutionInfo actualExecutionInfo = letterStatusResponseProcessor.process(externalIdentifierJobState, statusResponse, CHECK_LETTER_STATUS, letterAcceptedNextTaskExecutionHandler);

        assertThat(actualExecutionInfo, is(expectedExecutionInfo));

        verify(retryHandler).handle(notificationId, CHECK_LETTER_STATUS, PENDING_VIRUS_CHECK);
    }

    @Test
    public void shouldProcessInvalidRequestStatus() {

        final UUID notificationId = UUID.randomUUID();
        final ExternalIdentifierJobState externalIdentifierJobState = mock(ExternalIdentifierJobState.class);
        final StatusResponse statusResponse = mock(StatusResponse.class);
        final ExecutionInfo expectedExecutionInfo = mock(ExecutionInfo.class);
        final LetterAcceptedNextTaskExecutionHandler letterAcceptedNextTaskExecutionHandler = mock(LetterAcceptedNextTaskExecutionHandler.class);

        when(statusResponse.isSuccessful()).thenReturn(true);
        when(statusResponse.getNotificationStatus()).thenReturn(INVALID_REQUEST);
        when(externalIdentifierJobState.getNotificationId()).thenReturn(notificationId);
        final String errorMessage = format("Validation failed for '%s'", externalIdentifierJobState.getNotificationId());
        final ZonedDateTime failedTime = now();
        when(utcClock.now()).thenReturn(failedTime);

        when(invalidRequestHandler.handle(externalIdentifierJobState, errorMessage, failedTime)).thenReturn(expectedExecutionInfo);

        final ExecutionInfo actualExecutionInfo = letterStatusResponseProcessor.process(externalIdentifierJobState, statusResponse, CHECK_LETTER_STATUS, letterAcceptedNextTaskExecutionHandler);

        assertThat(actualExecutionInfo, is(expectedExecutionInfo));
    }

    @Test
    public void shouldProcessPermanentFailureDueToFailureStatus() {

        final UUID notificationId = UUID.randomUUID();
        final ExternalIdentifierJobState externalIdentifierJobState = mock(ExternalIdentifierJobState.class);
        final StatusResponse statusResponse = mock(StatusResponse.class);
        final ExecutionInfo expectedExecutionInfo = mock(ExecutionInfo.class);
        final LetterAcceptedNextTaskExecutionHandler letterAcceptedNextTaskExecutionHandler = mock(LetterAcceptedNextTaskExecutionHandler.class);
        final String expectedMessage = "Gov.Notify responded with status 'technical-failure'";

        when(statusResponse.getNotificationStatus()).thenReturn(FAILED);
        when(externalIdentifierJobState.getNotificationId()).thenReturn(notificationId);
        when(permanentFailureHandler.handle(
                notificationId,
                CHECK_LETTER_STATUS,
                expectedMessage)).thenReturn(expectedExecutionInfo);

        final ExecutionInfo actualExecutionInfo = letterStatusResponseProcessor.process(
                externalIdentifierJobState,
                statusResponse,
                CHECK_LETTER_STATUS, letterAcceptedNextTaskExecutionHandler);

        assertThat(actualExecutionInfo, is(expectedExecutionInfo));

        verify(permanentFailureHandler).handle(
                notificationId,
                CHECK_LETTER_STATUS,
                expectedMessage);
    }
}