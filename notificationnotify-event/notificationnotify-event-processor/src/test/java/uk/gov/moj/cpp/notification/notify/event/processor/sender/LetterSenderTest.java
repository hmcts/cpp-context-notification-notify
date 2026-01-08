package uk.gov.moj.cpp.notification.notify.event.processor.sender;

import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.notification.notify.event.processor.client.GovNotifyClientProvider;
import uk.gov.moj.cpp.notification.notify.event.processor.download.DocumentDownloadClient;
import uk.gov.moj.cpp.notification.notify.event.processor.download.SuccessfulDocumentDownload;
import uk.gov.moj.cpp.notification.notify.event.processor.download.UnsuccessfulDocumentDownload;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.NotificationResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.SenderResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.SendLetterDetails;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.SendLetterDetailsJobState;
import uk.gov.service.notify.LetterResponse;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;

import java.io.InputStream;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
public class LetterSenderTest {

    @Mock
    private GovNotifyClientProvider govNotifyClientProvider;

    @Mock
    private DocumentDownloadClient documentDownloadClient;

    @Mock
    private Logger logger;

    @InjectMocks
    private LetterSender letterSender;

    @Test
    public void shouldDownloadPdfAndSendToGovNotifyForFirstClassPostage() throws Exception {

        final String documentUrl = "document url";
        final UUID notificationId = randomUUID();
        final UUID externalNotificationId = randomUUID();

        final InputStream letterInputStream = mock(InputStream.class);
        final NotificationClient notificationClient = mock(NotificationClient.class);
        final LetterResponse letterResponse = mock(LetterResponse.class);

        final SendLetterDetailsJobState sendLetterDetailsJobState = new SendLetterDetailsJobState(
                notificationId,
                new SendLetterDetails(documentUrl, "first")
        );

        final int contentSize = 7834;
        final SuccessfulDocumentDownload successfulDocumentDownload = new SuccessfulDocumentDownload(200, letterInputStream, contentSize, "content".getBytes(), "sampleFile.pdf");

        when(documentDownloadClient.getDocument(documentUrl)).thenReturn(successfulDocumentDownload);
        when(govNotifyClientProvider.getClient()).thenReturn(notificationClient);
        when(notificationClient.sendPrecompiledLetterWithInputStream(
                notificationId.toString(),
                letterInputStream, "first")).thenReturn(letterResponse);
        when(letterResponse.getNotificationId()).thenReturn(externalNotificationId);

        final NotificationResponse notificationResponse = letterSender.send(sendLetterDetailsJobState);

        assertThat(notificationResponse.isSuccessful(), is(true));
        assertThat(((SenderResponse) notificationResponse).getExternalNotificationId(), is(externalNotificationId));

        verify(logger).info("Sending letter with notification Id '" + notificationId + "'");
        verify(logger).info("Download successful for Notification:{}, with download content size of {}", notificationId, contentSize);

        verify(letterInputStream).close();


    }

    @Test
    public void shouldDownloadPdfAndSendToGovNotifyForSecondClassPostage() throws Exception {

        final String documentUrl = "document url";
        final UUID notificationId = randomUUID();
        final UUID externalNotificationId = randomUUID();

        final InputStream letterInputStream = mock(InputStream.class);
        final NotificationClient notificationClient = mock(NotificationClient.class);
        final LetterResponse letterResponse = mock(LetterResponse.class);

        final SendLetterDetailsJobState sendLetterDetailsJobState = new SendLetterDetailsJobState(
                notificationId,
                new SendLetterDetails(documentUrl, "")
        );

        final int contentSize = 7834;
        final SuccessfulDocumentDownload successfulDocumentDownload = new SuccessfulDocumentDownload(200, letterInputStream, contentSize, "content".getBytes(), "sampleFile.pdf");

        when(documentDownloadClient.getDocument(documentUrl)).thenReturn(successfulDocumentDownload);
        when(govNotifyClientProvider.getClient()).thenReturn(notificationClient);
        when(notificationClient.sendPrecompiledLetterWithInputStream(
                notificationId.toString(),
                letterInputStream)).thenReturn(letterResponse);
        when(letterResponse.getNotificationId()).thenReturn(externalNotificationId);

        final NotificationResponse notificationResponse = letterSender.send(sendLetterDetailsJobState);

        assertThat(notificationResponse.isSuccessful(), is(true));
        assertThat(((SenderResponse) notificationResponse).getExternalNotificationId(), is(externalNotificationId));

        verify(logger).info("Sending letter with notification Id '" + notificationId + "'");
        verify(logger).info("Download successful for Notification:{}, with download content size of {}", notificationId, contentSize);

        verify(letterInputStream).close();


    }

    @Test
    public void shouldReturnAnErrorResponseIfSendToGovNotifyFails() throws Exception {
        final NotificationClientException notificationClientException = mock(NotificationClientException.class);


        final String errorMessage = "error message";
        final int statusCode = 500;

        final String documentUrl = "document url";
        final UUID notificationId = randomUUID();

        final InputStream letterInputStream = mock(InputStream.class);
        final NotificationClient notificationClient = mock(NotificationClient.class);

        final SendLetterDetailsJobState sendLetterDetailsJobState = new SendLetterDetailsJobState(
                notificationId,
                new SendLetterDetails(documentUrl, "first")
        );

        final SuccessfulDocumentDownload successfulDocumentDownload = new SuccessfulDocumentDownload(200, letterInputStream, 7834, "content".getBytes(), "sampleFile.pdf");

        when(documentDownloadClient.getDocument(documentUrl)).thenReturn(successfulDocumentDownload);
        when(govNotifyClientProvider.getClient()).thenReturn(notificationClient);
        when(notificationClient.sendPrecompiledLetterWithInputStream(
                notificationId.toString(),
                letterInputStream,"first")).thenThrow(notificationClientException);

        when(notificationClientException.getLocalizedMessage()).thenReturn(errorMessage);
        when(notificationClientException.getHttpResult()).thenReturn(statusCode);

        final NotificationResponse notificationResponse = letterSender.send(sendLetterDetailsJobState);

        assertThat(notificationResponse.isSuccessful(), is(false));

        final ErrorResponse errorResponse = (ErrorResponse) notificationResponse;

        assertThat(errorResponse.getStatusCode(), is(statusCode));
        assertThat(errorResponse.getErrorMessage(), is("Gov.Notify responded with 'error message'"));

        verify(letterInputStream).close();
    }

    @Test
    public void shouldReturnAnErrorResponseIfRuntimeExceptionIsThrown() throws Exception {
        final RuntimeException runtimeException = mock(RuntimeException.class);

        final String errorMessage = "error message";
        final String documentUrl = "document url";
        final UUID notificationId = randomUUID();

        final InputStream letterInputStream = mock(InputStream.class);
        final NotificationClient notificationClient = mock(NotificationClient.class);

        final SendLetterDetailsJobState sendLetterDetailsJobState = new SendLetterDetailsJobState(
                notificationId,
                new SendLetterDetails(documentUrl, "first")
        );

        final SuccessfulDocumentDownload successfulDocumentDownload = new SuccessfulDocumentDownload(200, letterInputStream, 7834, "content".getBytes(), "sampleFile.pdf");

        when(documentDownloadClient.getDocument(documentUrl)).thenReturn(successfulDocumentDownload);
        when(govNotifyClientProvider.getClient()).thenReturn(notificationClient);
        when(notificationClient.sendPrecompiledLetterWithInputStream(
                notificationId.toString(),
                letterInputStream, "first")).thenThrow(runtimeException);

        when(runtimeException.getLocalizedMessage()).thenReturn(errorMessage);

        final NotificationResponse notificationResponse = letterSender.send(sendLetterDetailsJobState);

        assertThat(notificationResponse.isSuccessful(), is(false));

        final ErrorResponse errorResponse = (ErrorResponse) notificationResponse;

        assertThat(errorResponse.isSuccessful(), is(false));
        assertThat(errorResponse.getStatusCode(), is(0));
        assertThat(errorResponse.getErrorMessage(),
                is(format("Permanent failure, unexpected error while trying to deliver notification Id '%s', error message: '%s'", notificationId, errorMessage)));

        final InOrder inOrder = inOrder(logger);

        inOrder.verify(logger).info(format("Sending letter with notification Id '%s'", notificationId));
        inOrder.verify(logger).error("An unexpected error was thrown while sending letter", runtimeException);

        verify(letterInputStream).close();
    }

    @Test
    public void shouldReturnAnErrorIfTheLetterPdfCannotBeDownloaded() throws Exception {

        final String documentUrl = "document url";
        final UUID notificationId = randomUUID();
        final int httpResult = 500;

        final SendLetterDetailsJobState sendLetterDetailsJobState = new SendLetterDetailsJobState(
                notificationId,
                new SendLetterDetails(documentUrl, "first")
        );

        final int contentSize = 0;
        final UnsuccessfulDocumentDownload unsuccessfulDocumentDownload = new UnsuccessfulDocumentDownload(httpResult, "It failed");

        when(documentDownloadClient.getDocument(documentUrl)).thenReturn(unsuccessfulDocumentDownload);

        final NotificationResponse notificationResponse = letterSender.send(sendLetterDetailsJobState);

        assertThat(notificationResponse.isSuccessful(), is(false));
        final ErrorResponse errorResponse = (ErrorResponse) notificationResponse;

        assertThat(errorResponse.getStatusCode(), is(httpResult));
        assertThat(errorResponse.getErrorMessage(), is("Failed to download pdf. Error message: 'It failed'"));

        verify(logger).info("Sending letter with notification Id '" + notificationId + "'");
        verify(logger).info("Download failed for Notification: {}", notificationId);
    }

    @Test
    public void shouldReturnAnErrorIfResponseIsNullWhenPdfDownloaded() throws Exception {

        final String documentUrl = "document url";
        final UUID notificationId = randomUUID();
        final int httpResult = 0;

        final SendLetterDetailsJobState sendLetterDetailsJobState = new SendLetterDetailsJobState(
                notificationId,
                new SendLetterDetails(documentUrl, "first")
        );

        when(documentDownloadClient.getDocument(documentUrl)).thenReturn(null);

        final NotificationResponse notificationResponse = letterSender.send(sendLetterDetailsJobState);

        assertThat(notificationResponse.isSuccessful(), is(false));
        final ErrorResponse errorResponse = (ErrorResponse) notificationResponse;

        assertThat(errorResponse.getStatusCode(), is(httpResult));
        assertThat(errorResponse.getErrorMessage(), is(format("Document download failed for Notification: %s", notificationId)));

        verify(logger).info("Sending letter with notification Id '" + notificationId + "'");
    }
}
