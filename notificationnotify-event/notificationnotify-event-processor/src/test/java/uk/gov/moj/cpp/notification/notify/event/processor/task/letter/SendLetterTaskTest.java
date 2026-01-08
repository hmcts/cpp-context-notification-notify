package uk.gov.moj.cpp.notification.notify.event.processor.task.letter;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_LETTER_STATUS;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.SEND_LETTER;

import java.util.List;
import java.util.Optional;
import org.hamcrest.CoreMatchers;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.SenderResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.retry.RetryService;
import uk.gov.moj.cpp.notification.notify.event.processor.sender.NotificationSender;
import uk.gov.moj.cpp.notification.notify.event.processor.sender.SenderFactory;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.SendLetterDetails;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.SendLetterDetailsJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.task.processors.SenderErrorResponseProcessor;
import uk.gov.moj.cpp.notification.notify.event.processor.task.processors.SuccessfulResponseProcessor;

import java.util.UUID;

import javax.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
public class SendLetterTaskTest {

    @Mock
    private SenderFactory senderFactory;

    @Mock
    private JsonObjectToObjectConverter jsonObjectConverter;

    @Mock
    private SuccessfulResponseProcessor successfulResponseProcessor;

    @Mock
    private SenderErrorResponseProcessor senderErrorResponseProcessor;

    @Mock
    private Logger logger;

    @Mock
    private RetryService retryService;

    @InjectMocks
    private SendLetterTask sendLetterTask;

    @Test
    public void shouldSendLetterNotification() throws Exception {

        final UUID notificationId = randomUUID();
        final ExecutionInfo sendLetterExecutionInfo = mock(ExecutionInfo.class);
        final ExecutionInfo expectedExecutionInfo = mock(ExecutionInfo.class);
        final JsonObject jobData = mock(JsonObject.class);
        final SendLetterDetailsJobState sendLetterDetailsJobState = mock(SendLetterDetailsJobState.class);
        final SendLetterDetails sendLetterDetails = mock(SendLetterDetails.class);
        final NotificationSender letterSender = mock(NotificationSender.class);
        final SenderResponse senderResponse = mock(SenderResponse.class);

        when(sendLetterExecutionInfo.getJobData()).thenReturn(jobData);
        when(jsonObjectConverter.convert(jobData, SendLetterDetailsJobState.class)).thenReturn(sendLetterDetailsJobState);
        when(sendLetterDetailsJobState.getNotificationId()).thenReturn(notificationId);
        when(sendLetterDetailsJobState.getTaskPayload()).thenReturn(sendLetterDetails);
        when(sendLetterDetails.getPostage()).thenReturn("first");
        when(senderFactory.senderFor(SEND_LETTER)).thenReturn(letterSender);
        when(letterSender.send(sendLetterDetailsJobState)).thenReturn(senderResponse);
        when(senderResponse.isSuccessful()).thenReturn(true);
        when(successfulResponseProcessor.handleSuccessfulResponse(
                senderResponse,
                sendLetterDetailsJobState,
                CHECK_LETTER_STATUS)).thenReturn(expectedExecutionInfo);

        final ExecutionInfo actualExecutionInfo = sendLetterTask.execute(sendLetterExecutionInfo);

        assertThat(actualExecutionInfo, is(expectedExecutionInfo));

        verify(logger).debug("Executing SEND LETTER task {}", notificationId);
        verify(successfulResponseProcessor).handleSuccessfulResponse(
                senderResponse,
                sendLetterDetailsJobState,
                CHECK_LETTER_STATUS);
    }

    @Test
    public void shouldHandleErrorResponse() throws Exception {

        final UUID notificationId = randomUUID();
        final ExecutionInfo sendLetterExecutionInfo = mock(ExecutionInfo.class);
        final ExecutionInfo expectedExecutionInfo = mock(ExecutionInfo.class);
        final JsonObject jobData = mock(JsonObject.class);
        final SendLetterDetailsJobState sendLetterDetailsJobState = mock(SendLetterDetailsJobState.class);
        final SendLetterDetails sendLetterDetails = mock(SendLetterDetails.class);
        final NotificationSender letterSender = mock(NotificationSender.class);
        final ErrorResponse errorResponse = mock(ErrorResponse.class);

        when(sendLetterExecutionInfo.getJobData()).thenReturn(jobData);
        when(jsonObjectConverter.convert(jobData, SendLetterDetailsJobState.class)).thenReturn(sendLetterDetailsJobState);
        when(sendLetterDetailsJobState.getNotificationId()).thenReturn(notificationId);
        when(sendLetterDetailsJobState.getTaskPayload()).thenReturn(sendLetterDetails);
        when(sendLetterDetails.getPostage()).thenReturn("first");
        when(senderFactory.senderFor(SEND_LETTER)).thenReturn(letterSender);
        when(letterSender.send(sendLetterDetailsJobState)).thenReturn(errorResponse);
        when(errorResponse.isSuccessful()).thenReturn(false);
        when(senderErrorResponseProcessor.process(
                sendLetterDetailsJobState,
                errorResponse,
                SEND_LETTER)).thenReturn(expectedExecutionInfo);

        final ExecutionInfo actualExecutionInfo = sendLetterTask.execute(sendLetterExecutionInfo);

        assertThat(actualExecutionInfo, is(expectedExecutionInfo));

        verify(logger).debug("Executing SEND LETTER task {}", notificationId);
        verify(senderErrorResponseProcessor).process(
                sendLetterDetailsJobState,
                errorResponse,
                SEND_LETTER);
    }

    @Test
    public void shouldReturnRetryDurations() {
        final Optional<List<Long>> durations = Optional.of(List.of(1L, 2L));

        when(retryService.getRetryDurationsInSecs(SEND_LETTER)).thenReturn(durations);

        final Optional<List<Long>> result = sendLetterTask.getRetryDurationsInSecs();

        assertThat(result, CoreMatchers.is(durations));
    }
}
