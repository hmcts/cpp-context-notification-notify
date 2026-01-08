package uk.gov.moj.cpp.notification.notify.event.processor.task.email;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_EMAIL_STATUS;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.SEND_EMAIL;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.SenderResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.retry.RetryService;
import uk.gov.moj.cpp.notification.notify.event.processor.sender.NotificationSender;
import uk.gov.moj.cpp.notification.notify.event.processor.sender.SenderFactory;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.SendEmailDetailsJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.task.processors.SenderErrorResponseProcessor;
import uk.gov.moj.cpp.notification.notify.event.processor.task.processors.SuccessfulResponseProcessor;

import java.util.List;
import java.util.Optional;

import javax.json.JsonObject;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
public class SendEmailTaskTest {

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
    private SendEmailTask sendEmailTask;


    @Test
    public void shouldSendEmailNotification() {

        final ExecutionInfo sendEmailExecutionInfo = mock(ExecutionInfo.class);
        final ExecutionInfo expectedExecutionInfo = mock(ExecutionInfo.class);
        final JsonObject jobData = mock(JsonObject.class);
        final SendEmailDetailsJobState sendEmailDetailsJobState = mock(SendEmailDetailsJobState.class);
        final NotificationSender emailSender = mock(NotificationSender.class);
        final SenderResponse senderResponse = mock(SenderResponse.class);

        when(sendEmailExecutionInfo.getJobData()).thenReturn(jobData);
        when(jsonObjectConverter.convert(jobData, SendEmailDetailsJobState.class)).thenReturn(sendEmailDetailsJobState);
        when(senderFactory.senderFor(SEND_EMAIL)).thenReturn(emailSender);
        when(emailSender.send(sendEmailDetailsJobState)).thenReturn(senderResponse);
        when(senderResponse.isSuccessful()).thenReturn(true);
        when(successfulResponseProcessor.handleSuccessfulResponse(
                senderResponse,
                sendEmailDetailsJobState,
                CHECK_EMAIL_STATUS)).thenReturn(expectedExecutionInfo);

        final ExecutionInfo actualExecutionInfo = sendEmailTask.execute(sendEmailExecutionInfo);

        assertThat(actualExecutionInfo, is(expectedExecutionInfo));

        verify(successfulResponseProcessor).handleSuccessfulResponse(
                senderResponse,
                sendEmailDetailsJobState,
                CHECK_EMAIL_STATUS);
    }

    @Test
    public void shouldHandleErrorResponse() {


        final ExecutionInfo sendEmailExecutionInfo = mock(ExecutionInfo.class);
        final ExecutionInfo expectedExecutionInfo = mock(ExecutionInfo.class);
        final JsonObject jobData = mock(JsonObject.class);
        final SendEmailDetailsJobState sendEmailDetailsJobState = mock(SendEmailDetailsJobState.class);
        final NotificationSender emailSender = mock(NotificationSender.class);
        final ErrorResponse errorResponse = mock(ErrorResponse.class);

        when(sendEmailExecutionInfo.getJobData()).thenReturn(jobData);
        when(jsonObjectConverter.convert(jobData, SendEmailDetailsJobState.class)).thenReturn(sendEmailDetailsJobState);
        when(senderFactory.senderFor(SEND_EMAIL)).thenReturn(emailSender);
        when(emailSender.send(sendEmailDetailsJobState)).thenReturn(errorResponse);
        when(errorResponse.isSuccessful()).thenReturn(false);
        when(senderErrorResponseProcessor.process(
                sendEmailDetailsJobState,
                errorResponse,
                SEND_EMAIL)).thenReturn(expectedExecutionInfo);

        final ExecutionInfo actualExecutionInfo = sendEmailTask.execute(sendEmailExecutionInfo);

        assertThat(actualExecutionInfo, is(expectedExecutionInfo));


        verify(senderErrorResponseProcessor).process(
                sendEmailDetailsJobState,
                errorResponse,
                SEND_EMAIL);
    }

    @Test
    public void shouldReturnRetryDurations() {
        final Optional<List<Long>> durations = Optional.of(List.of(1L, 2L));

        when(retryService.getRetryDurationsInSecs(SEND_EMAIL)).thenReturn(durations);

        final Optional<List<Long>> result = sendEmailTask.getRetryDurationsInSecs();

        assertThat(result, CoreMatchers.is(durations));
    }
}
