package uk.gov.moj.cpp.notification.notify.event.processor.task.handlers;


import java.time.ZonedDateTime;
import java.util.UUID;
import javax.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExternalIdentifier;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExternalIdentifierJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExtractedSendEmailResponse;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.TaskNames.CHECK_ACCEPTED_LETTER_STATUS_TASK;


@ExtendWith(MockitoExtension.class)
public class LetterAcceptedNextTaskExecutionHandlerTest {

    @Mock
    private UtcClock utcClock;

    @Mock
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;

    @InjectMocks
    private LetterAcceptedNextTaskExecutionHandler letterAcceptedNextTaskExecutionHandler;

    @Test
    public void shouldConfigureLetterAcceptedTaskAsNextTask() throws Exception {

        final UUID notificationId = randomUUID();
        ExtractedSendEmailResponse extractedSendEmailResponse = mock(ExtractedSendEmailResponse.class);
        final ExternalIdentifier taskPayload = new ExternalIdentifier(randomUUID(),extractedSendEmailResponse);
        final ExternalIdentifierJobState jobState = new ExternalIdentifierJobState(
                notificationId,
                taskPayload
        );
        final ZonedDateTime now = ZonedDateTime.now();
        final JsonObject jsonObject = mock(JsonObject.class);
        when(utcClock.now()).thenReturn(now);
        when(objectToJsonObjectConverter.convert(jobState)).thenReturn(jsonObject);

        final ExecutionInfo executionInfo = letterAcceptedNextTaskExecutionHandler.handle(jobState);

        assertThat(executionInfo.getNextTask(), is(CHECK_ACCEPTED_LETTER_STATUS_TASK));
        assertThat(executionInfo.getNextTaskStartTime(), is(now));
        assertThat(executionInfo.getExecutionStatus(), is(ExecutionStatus.INPROGRESS));
        assertThat(executionInfo.getJobData(), is(jsonObject));
    }
}
