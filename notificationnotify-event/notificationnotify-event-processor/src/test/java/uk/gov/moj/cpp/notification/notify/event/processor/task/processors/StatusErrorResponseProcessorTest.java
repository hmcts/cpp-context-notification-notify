package uk.gov.moj.cpp.notification.notify.event.processor.task.processors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_EMAIL_STATUS;

import java.util.UUID;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExternalIdentifierJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.RetryHandler;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.PermanentFailureHandler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class StatusErrorResponseProcessorTest {

    @Mock
    private RetryHandler retryHandler;

    @Mock
    private PermanentFailureHandler permanentFailureHandler;

    @Mock
    private FailureSelector failureSelector;

    @InjectMocks
    private StatusErrorResponseProcessor statusErrorResponseProcessor;

    @Test
    public void shouldProcessTemporaryFailure() {

        final UUID notificationId = UUID.randomUUID();
        final ExternalIdentifierJobState externalIdentifierJobState = mock(ExternalIdentifierJobState.class);
        final ErrorResponse errorResponse = mock(ErrorResponse.class);
        final ExecutionInfo expectedExecutionInfo = mock(ExecutionInfo.class);

        when(failureSelector.isTemporaryFailure(errorResponse)).thenReturn(true);
        when(externalIdentifierJobState.getNotificationId()).thenReturn(notificationId);
        when(retryHandler.handle(notificationId, CHECK_EMAIL_STATUS, errorResponse)).thenReturn(expectedExecutionInfo);

        final ExecutionInfo actualExecutionInfo = statusErrorResponseProcessor.process(
                externalIdentifierJobState,
                errorResponse,
                CHECK_EMAIL_STATUS);

        assertThat(actualExecutionInfo, is(expectedExecutionInfo));

        verify(retryHandler).handle(notificationId, CHECK_EMAIL_STATUS, errorResponse);
    }

    @Test
    public void shouldProcessPermanentFailure() {

        final UUID notificationId = UUID.randomUUID();
        final ExternalIdentifierJobState externalIdentifierJobState = mock(ExternalIdentifierJobState.class);
        final ErrorResponse errorResponse = mock(ErrorResponse.class);
        final ExecutionInfo expectedExecutionInfo = mock(ExecutionInfo.class);

        when(failureSelector.isTemporaryFailure(errorResponse)).thenReturn(false);
        when(externalIdentifierJobState.getNotificationId()).thenReturn(notificationId);

        when(permanentFailureHandler.handle(
                notificationId,
                errorResponse,
                CHECK_EMAIL_STATUS)).thenReturn(expectedExecutionInfo);

        final ExecutionInfo actualExecutionInfo = statusErrorResponseProcessor.process(
                externalIdentifierJobState,
                errorResponse,
                CHECK_EMAIL_STATUS);

        assertThat(actualExecutionInfo, is(expectedExecutionInfo));

        verify(permanentFailureHandler).handle(
                notificationId,
                errorResponse,
                CHECK_EMAIL_STATUS);
    }
}