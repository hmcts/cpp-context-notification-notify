package uk.gov.moj.cpp.notification.notify.event.processor.task.handlers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExternalIdentifier;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExternalIdentifierJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExtractedSendEmailResponse;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_ACCEPTED_LETTER_STATUS;

@ExtendWith(MockitoExtension.class)
class LetterAcceptedRetryHandlerTest {

    @Mock
    private RetryHandler retryHandler;

    @InjectMocks
    private LetterAcceptedRetryHandler letterAcceptedRetryHandler;

    @Test
    public void shouldConfigureRetryTask() throws Exception {
        final ExternalIdentifierJobState jobState = new ExternalIdentifierJobState(
                randomUUID(),
                new ExternalIdentifier(randomUUID(), mock(ExtractedSendEmailResponse.class))
        );
        final ExecutionInfo executionInfo = ExecutionInfo.executionInfo().build();
        when(retryHandler.handle(any(), any(), any(NotificationStatus.class))).thenReturn(executionInfo);

        final ExecutionInfo actualExecutionInfo = letterAcceptedRetryHandler.handle(jobState);

        assertThat(actualExecutionInfo, is(executionInfo));
        verify(retryHandler).handle(jobState.getNotificationId(),
                CHECK_ACCEPTED_LETTER_STATUS,
                NotificationStatus.ACCEPTED);
    }
}