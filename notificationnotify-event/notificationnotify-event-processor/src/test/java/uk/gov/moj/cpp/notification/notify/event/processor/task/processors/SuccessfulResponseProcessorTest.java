package uk.gov.moj.cpp.notification.notify.event.processor.task.processors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.INPROGRESS;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_EMAIL_STATUS;

import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.metrics.Metrics;
import uk.gov.moj.cpp.notification.notify.event.processor.response.SenderResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExternalIdentifierJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.NotificationJobState;

import java.time.ZonedDateTime;

import javax.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SuccessfulResponseProcessorTest {

    @Mock
    private Metrics metrics;

    @Mock
    private ObjectToJsonObjectConverter objectConverter;

    @Mock
    private UtcClock clock;

    @InjectMocks
    private SuccessfulResponseProcessor successfulResponseProcessor;

    @Test
    public void shouldProcessSuccessfulResponse() {
        final SenderResponse senderResponse = mock(SenderResponse.class);
        final NotificationJobState notificationJobState = mock(NotificationJobState.class);
        final ExternalIdentifierJobState externalIdentifierJobState = mock(ExternalIdentifierJobState.class);
        final JsonObject jsonObject = mock(JsonObject.class);
        final ZonedDateTime nextStartTime = ZonedDateTime.now();
        when(objectConverter.convert(any())).thenReturn(jsonObject);
        when(clock.now()).thenReturn(nextStartTime);

        final ExecutionInfo actualExecutionInfo = successfulResponseProcessor.handleSuccessfulResponse(senderResponse, notificationJobState, CHECK_EMAIL_STATUS);

        assertThat(actualExecutionInfo.getJobData(), is(jsonObject));
        assertThat(actualExecutionInfo.getNextTask(), is(CHECK_EMAIL_STATUS.getTaskName()));
        assertThat(actualExecutionInfo.getNextTaskStartTime(), is(nextStartTime));
        assertThat(actualExecutionInfo.getExecutionStatus(), is(INPROGRESS));

        verify(metrics).incrementSentCounter(CHECK_EMAIL_STATUS);
        verify(objectConverter).convert(argThat(jobState -> {
            final ExternalIdentifierJobState actualValue = (ExternalIdentifierJobState) jobState;
            assertThat(actualValue.getNotificationId(), is(notificationJobState.getNotificationId()));
            assertThat(actualValue.getTaskPayload().getExternalNotificationId(), is(senderResponse.getExternalNotificationId()));
            return true;
        }));
    }
}