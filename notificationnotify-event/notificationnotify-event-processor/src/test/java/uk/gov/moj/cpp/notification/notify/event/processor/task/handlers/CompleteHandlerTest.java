package uk.gov.moj.cpp.notification.notify.event.processor.task.handlers;

import static java.util.Optional.of;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.COMPLETED;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_EMAIL_STATUS;

import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.NotificationNotifyCommandSender;
import uk.gov.moj.cpp.notification.notify.event.processor.client.GovNotifyClientProvider;
import uk.gov.moj.cpp.notification.notify.event.processor.metrics.Metrics;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExternalIdentifier;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExtractedSendEmailResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.NotificationJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.SendEmailDetails;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.SendEmailDetailsJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.util.PersonalisationExtractor;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CompleteHandlerTest {

    @Mock
    private NotificationNotifyCommandSender notificationNotifyCommandSender;

    @Mock
    private Metrics metrics;

    @InjectMocks
    private CompleteHandler completeHandler;
    @Mock
    private PersonalisationExtractor personalisationExtractor;
    @Mock
    private GovNotifyClientProvider govNotifyClientProvider;
    @Mock
    private NotificationClient notificationClient;

    @Test
    public void shouldHandleEmailDelivered() {
        final UUID notificationId = randomUUID();
        ExtractedSendEmailResponse extractedSendEmailResponse = new ExtractedSendEmailResponse(false, "email subject", "email body", "replyToAddressId", "sendToAddress");

        ExternalIdentifier externalIdentifier = new ExternalIdentifier(notificationId, extractedSendEmailResponse) ;
        NotificationJobState notificationJobState = new NotificationJobState<>(notificationId, externalIdentifier);

        final ExecutionInfo executionInfo = completeHandler.handle(notificationJobState, CHECK_EMAIL_STATUS);

        assertThat(executionInfo.getExecutionStatus(), is(COMPLETED));
        verify(notificationNotifyCommandSender).markAsSent(any(), any());
        verify(metrics).incrementSuccessCounter(CHECK_EMAIL_STATUS);
    }
}
