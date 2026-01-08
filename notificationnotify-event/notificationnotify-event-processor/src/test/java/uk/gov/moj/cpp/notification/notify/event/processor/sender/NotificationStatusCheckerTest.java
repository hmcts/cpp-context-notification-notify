package uk.gov.moj.cpp.notification.notify.event.processor.sender;

import static java.lang.String.format;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus.DELIVERED;

import uk.gov.moj.cpp.notification.notify.event.processor.client.GovNotifyClientProvider;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.StatusResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExternalIdentifier;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExternalIdentifierJobState;
import uk.gov.service.notify.Notification;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
public class NotificationStatusCheckerTest {

    @Mock
    private GovNotifyClientProvider govNotifyClientProvider;

    @Mock
    private Logger logger;

    @InjectMocks
    private NotificationStatusChecker notificationStatusChecker;

    @Test
    public void shouldReturnStatusResponseIfSuccessfulStatusResponse() throws Exception {

        final UUID govNotifyNotificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");

        final ExternalIdentifierJobState externalIdentifierJobState = mock(ExternalIdentifierJobState.class);
        final ExternalIdentifier externalIdentifier = mock(ExternalIdentifier.class);
        final NotificationClient notificationClient = mock(NotificationClient.class);
        final Notification notification = mock(Notification.class);

        when(externalIdentifierJobState.getTaskPayload()).thenReturn(externalIdentifier);
        when(externalIdentifier.getExternalNotificationId()).thenReturn(govNotifyNotificationId);
        when(govNotifyClientProvider.getClient()).thenReturn(notificationClient);
        when(notificationClient.getNotificationById(govNotifyNotificationId.toString())).thenReturn(notification);
        when(notification.getStatus()).thenReturn("delivered");

        final StatusResponse statusResponse = (StatusResponse) notificationStatusChecker.checkStatus(externalIdentifierJobState);

        assertThat(statusResponse.isSuccessful(), is(true));
        assertThat(statusResponse.getNotificationStatus(), is(DELIVERED));

        verify(logger).info("Checking notification status of notification Id '0bc5cacf-245b-488c-bc95-2ab182977d2d'");
    }

    @Test
    public void shouldReturnErrorResponseIfNotificationClientExceptionIsThrown() throws Exception {

        final UUID govNotifyNotificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final NotificationClientException notificationClientException = mock(NotificationClientException.class);
        final String govNotifyMessage = "Message";

        final ExternalIdentifierJobState externalIdentifierJobState = mock(ExternalIdentifierJobState.class);
        final ExternalIdentifier externalIdentifier = mock(ExternalIdentifier.class);
        final NotificationClient notificationClient = mock(NotificationClient.class);

        when(externalIdentifierJobState.getTaskPayload()).thenReturn(externalIdentifier);
        when(externalIdentifier.getExternalNotificationId()).thenReturn(govNotifyNotificationId);
        when(govNotifyClientProvider.getClient()).thenReturn(notificationClient);
        when(notificationClient.getNotificationById(govNotifyNotificationId.toString())).thenThrow(notificationClientException);
        when(notificationClientException.getHttpResult()).thenReturn(SC_NOT_FOUND);
        when(notificationClientException.getLocalizedMessage()).thenReturn(govNotifyMessage);

        final ErrorResponse errorResponse = (ErrorResponse) notificationStatusChecker.checkStatus(externalIdentifierJobState);

        assertThat(errorResponse.isSuccessful(), is(false));
        assertThat(errorResponse.getStatusCode(), is(SC_NOT_FOUND));
        assertThat(errorResponse.getErrorMessage(), is(format("Gov.Notify responded with '%s'", govNotifyMessage)));

        final InOrder inOrder = inOrder(logger);

        inOrder.verify(logger).info("Checking notification status of notification Id '0bc5cacf-245b-488c-bc95-2ab182977d2d'");
        inOrder.verify(logger).error("An error was thrown while checking the email status", notificationClientException);
    }

    @Test
    public void shouldReturnErrorResponseIfRuntimeExceptionIsThrown() throws Exception {

        final UUID notificationId = randomUUID();
        final UUID govNotifyNotificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final RuntimeException runtimeException = mock(RuntimeException.class);
        final String exceptionMessage = "Message";

        final ExternalIdentifierJobState externalIdentifierJobState = mock(ExternalIdentifierJobState.class);
        final ExternalIdentifier externalIdentifier = mock(ExternalIdentifier.class);
        final NotificationClient notificationClient = mock(NotificationClient.class);

        when(externalIdentifierJobState.getTaskPayload()).thenReturn(externalIdentifier);
        when(externalIdentifier.getExternalNotificationId()).thenReturn(govNotifyNotificationId);
        when(govNotifyClientProvider.getClient()).thenReturn(notificationClient);
        when(notificationClient.getNotificationById(govNotifyNotificationId.toString())).thenThrow(runtimeException);
        when(runtimeException.getLocalizedMessage()).thenReturn(exceptionMessage);
        when(externalIdentifierJobState.getNotificationId()).thenReturn(notificationId);

        final ErrorResponse errorResponse = (ErrorResponse) notificationStatusChecker.checkStatus(externalIdentifierJobState);

        assertThat(errorResponse.isSuccessful(), is(false));
        assertThat(errorResponse.getStatusCode(), is(0));
        assertThat(errorResponse.getErrorMessage(),
                is(format("Permanent failure, unexpected error while trying to deliver notification Id '%s', error message: '%s'", notificationId, exceptionMessage)));

        final InOrder inOrder = inOrder(logger);

        inOrder.verify(logger).info("Checking notification status of notification Id '0bc5cacf-245b-488c-bc95-2ab182977d2d'");
        inOrder.verify(logger).error("An unexpected error was thrown while checking the email status", runtimeException);
    }
}
