package uk.gov.moj.cpp.notification.notify.event.processor.task.handlers;

import static java.time.ZonedDateTime.now;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus;
import uk.gov.moj.cpp.notification.notify.event.processor.NotificationNotifyCommandSender;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExternalIdentifier;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExternalIdentifierJobState;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExtractedSendEmailResponse;

@ExtendWith(MockitoExtension.class)
public class InvalidRequestHandlerTest {

    @Mock
    private NotificationNotifyCommandSender notificationNotifyCommandSender;

    @InjectMocks
    private InvalidRequestHandler invalidRequestHandler;

    @Test
    public void shouldHandleInvalid() {
        final UUID notificationId = randomUUID();
        ExtractedSendEmailResponse extractedSendEmailResponse = mock(ExtractedSendEmailResponse.class);
        final ExternalIdentifier taskPayload = new ExternalIdentifier(randomUUID(), extractedSendEmailResponse);

        final ExternalIdentifierJobState originalJobState = new ExternalIdentifierJobState(
                notificationId,
                taskPayload
        );

        final ZonedDateTime failedTime = now();
        final String errorMessage = "Validation failed";

        final ExecutionInfo actualExecutionInfo = invalidRequestHandler.handle(originalJobState, errorMessage, failedTime);

        assertThat(actualExecutionInfo.getExecutionStatus(), is(ExecutionStatus.COMPLETED));
        verify(notificationNotifyCommandSender).markAsInvalid(notificationId, errorMessage, failedTime);
    }

}