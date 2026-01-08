package uk.gov.moj.cpp.notification.notify.event.processor.task.letter;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.StatusResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.sender.NotificationStatusChecker;
import uk.gov.moj.cpp.notification.notify.event.processor.task.Task;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExternalIdentifierJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.LetterAcceptedNextTaskExecutionHandler;
import uk.gov.moj.cpp.notification.notify.event.processor.task.processors.LetterStatusResponseProcessor;
import uk.gov.moj.cpp.notification.notify.event.processor.task.processors.StatusErrorResponseProcessor;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_LETTER_STATUS;

@ExtendWith(MockitoExtension.class)
public class CheckLetterStatusHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    @Mock
    private JsonObjectToObjectConverter jsonObjectConverter = new JsonObjectToObjectConverter(objectMapper);;

    @Mock
    private NotificationStatusChecker notificationStatusChecker;

    @Mock
    private LetterStatusResponseProcessor letterStatusResponseProcessor;

    @Mock
    private StatusErrorResponseProcessor statusErrorResponseProcessor;

    @Mock
    private Logger logger;

    @InjectMocks
    private CheckLetterStatusHandler checkLetterStatusHandler;

    @Test
    public void shouldUpdateExecutionStatus() throws Exception {

        final UUID notificationId = randomUUID();
        final ExecutionInfo checkStatusExecutionInfo = mock(ExecutionInfo.class);
        final ExecutionInfo expectedExecutionInfo = mock(ExecutionInfo.class);
        final ExternalIdentifierJobState externalIdentifierJobState = mock(ExternalIdentifierJobState.class);
        final StatusResponse statusResponse = mock(StatusResponse.class);
        final LetterAcceptedNextTaskExecutionHandler letterAcceptedNextTaskExecutionHandler = mock(LetterAcceptedNextTaskExecutionHandler.class);

        when(jsonObjectConverter.convert(checkStatusExecutionInfo.getJobData(), ExternalIdentifierJobState.class)).thenReturn(externalIdentifierJobState);
        when(externalIdentifierJobState.getNotificationId()).thenReturn(notificationId);
        when(notificationStatusChecker.checkStatus(externalIdentifierJobState)).thenReturn(statusResponse);
        when(statusResponse.isSuccessful()).thenReturn(true);
        when(letterStatusResponseProcessor.process(
                externalIdentifierJobState, statusResponse, CHECK_LETTER_STATUS, letterAcceptedNextTaskExecutionHandler)).thenReturn(expectedExecutionInfo);

        final ExecutionInfo actualExecutionInfo = checkLetterStatusHandler.handle(checkStatusExecutionInfo, letterAcceptedNextTaskExecutionHandler, CHECK_LETTER_STATUS);

        assertThat(actualExecutionInfo, is(expectedExecutionInfo));

        verify(logger).debug("Executing CHECK_LETTER_STATUS task {}", notificationId);
        verify(letterStatusResponseProcessor).process(
                externalIdentifierJobState,
                statusResponse,
                CHECK_LETTER_STATUS, letterAcceptedNextTaskExecutionHandler);
    }

    @Test
    public void shouldHandleErrorResponse() throws Exception {

        final UUID notificationId = randomUUID();
        final ExecutionInfo checkStatusExecutionInfo = mock(ExecutionInfo.class);
        final ExecutionInfo expectedExecutionInfo = mock(ExecutionInfo.class);
        final ExternalIdentifierJobState externalIdentifierJobState = mock(ExternalIdentifierJobState.class);
        final ErrorResponse errorResponse = mock(ErrorResponse.class);
        final LetterAcceptedNextTaskExecutionHandler letterAcceptedNextTaskExecutionHandler = mock(LetterAcceptedNextTaskExecutionHandler.class);

        when(jsonObjectConverter.convert(checkStatusExecutionInfo.getJobData(), ExternalIdentifierJobState.class)).thenReturn(externalIdentifierJobState);
        when(externalIdentifierJobState.getNotificationId()).thenReturn(notificationId);
        when(notificationStatusChecker.checkStatus(externalIdentifierJobState)).thenReturn(errorResponse);
        when(errorResponse.isSuccessful()).thenReturn(false);
        when(statusErrorResponseProcessor.process(
                externalIdentifierJobState,
                errorResponse,
                CHECK_LETTER_STATUS)).thenReturn(expectedExecutionInfo);

        final ExecutionInfo actualExecutionInfo = checkLetterStatusHandler.handle(checkStatusExecutionInfo, letterAcceptedNextTaskExecutionHandler, CHECK_LETTER_STATUS);

        assertThat(actualExecutionInfo, is(expectedExecutionInfo));

        verify(logger).debug("Executing CHECK_LETTER_STATUS task {}", notificationId);
        verify(statusErrorResponseProcessor).process(
                externalIdentifierJobState,
                errorResponse,
                CHECK_LETTER_STATUS);
    }
}
