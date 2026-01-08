package uk.gov.moj.cpp.notification.notify.event.processor.task.email;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_EMAIL_STATUS;

import java.util.List;
import java.util.Optional;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.StatusResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.retry.RetryService;
import uk.gov.moj.cpp.notification.notify.event.processor.sender.NotificationStatusChecker;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExternalIdentifierJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.CompleteHandler;
import uk.gov.moj.cpp.notification.notify.event.processor.task.processors.EmailStatusResponseProcessor;
import uk.gov.moj.cpp.notification.notify.event.processor.task.processors.StatusErrorResponseProcessor;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
public class CheckEmailStatusTaskTest {

    private final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    @Mock
    private JsonObjectToObjectConverter jsonObjectConverter = new JsonObjectToObjectConverter(objectMapper);;

    @Mock
    private NotificationStatusChecker notificationStatusChecker;

    @Mock
    private EmailStatusResponseProcessor emailStatusResponseProcessor;

    @Mock
    private StatusErrorResponseProcessor statusErrorResponseProcessor;

    @Mock
    private CompleteHandler completeHandler;

    @Mock
    private Logger logger;

    @Mock
    private RetryService retryService;

    @InjectMocks
    private CheckEmailStatusTask checkEmailStatusTask;

    @Test
    public void shouldUpdateExecutionStatus() throws Exception {

        final UUID notificationId = randomUUID();
        final ExecutionInfo checkStatusExecutionInfo = mock(ExecutionInfo.class);
        final ExecutionInfo expectedExecutionInfo = mock(ExecutionInfo.class);
        final ExternalIdentifierJobState externalIdentifierJobState = mock(ExternalIdentifierJobState.class);
        final StatusResponse statusResponse = mock(StatusResponse.class);

        when(jsonObjectConverter.convert(checkStatusExecutionInfo.getJobData(), ExternalIdentifierJobState.class)).thenReturn(externalIdentifierJobState);
        when(externalIdentifierJobState.getNotificationId()).thenReturn(notificationId);
        when(notificationStatusChecker.checkStatus(externalIdentifierJobState)).thenReturn(statusResponse);
        when(statusResponse.isSuccessful()).thenReturn(true);
        when(emailStatusResponseProcessor.process(
                externalIdentifierJobState,
                statusResponse,
                CHECK_EMAIL_STATUS)).thenReturn(expectedExecutionInfo);

        final ExecutionInfo actualExecutionInfo = checkEmailStatusTask.execute(checkStatusExecutionInfo);

        assertThat(actualExecutionInfo, is(expectedExecutionInfo));

        verify(logger).debug("Executing CHECK EMAIL STATUS task {}", notificationId);
        verify(emailStatusResponseProcessor).process(
                externalIdentifierJobState,
                statusResponse,
                CHECK_EMAIL_STATUS);
    }

    @Test
    public void shouldHandleErrorResponse() throws Exception {

        final UUID notificationId = randomUUID();
        final ExecutionInfo checkStatusExecutionInfo = mock(ExecutionInfo.class);
        final ExecutionInfo expectedExecutionInfo = mock(ExecutionInfo.class);
        final ExternalIdentifierJobState externalIdentifierJobState = mock(ExternalIdentifierJobState.class);
        final ErrorResponse errorResponse = mock(ErrorResponse.class);

        when(jsonObjectConverter.convert(checkStatusExecutionInfo.getJobData(), ExternalIdentifierJobState.class)).thenReturn(externalIdentifierJobState);
        when(externalIdentifierJobState.getNotificationId()).thenReturn(notificationId);
        when(notificationStatusChecker.checkStatus(externalIdentifierJobState)).thenReturn(errorResponse);
        when(errorResponse.isSuccessful()).thenReturn(false);
        when(statusErrorResponseProcessor.process(
                externalIdentifierJobState,
                errorResponse,
                CHECK_EMAIL_STATUS)).thenReturn(expectedExecutionInfo);

        final ExecutionInfo actualExecutionInfo = checkEmailStatusTask.execute(checkStatusExecutionInfo);

        assertThat(actualExecutionInfo, is(expectedExecutionInfo));

        verify(logger).debug("Executing CHECK EMAIL STATUS task {}", notificationId);
        verify(statusErrorResponseProcessor).process(
                externalIdentifierJobState,
                errorResponse,
                CHECK_EMAIL_STATUS);
    }

    @Test
    public void shouldReturnRetryDurations() {
        final Optional<List<Long>> durations = Optional.of(List.of(1L, 2L));

        when(retryService.getRetryDurationsInSecs(CHECK_EMAIL_STATUS)).thenReturn(durations);

        final Optional<List<Long>> result = checkEmailStatusTask.getRetryDurationsInSecs();

        assertThat(result, is(durations));
    }
}
