package uk.gov.moj.cpp.notification.notify.event.processor.sender;

import static java.lang.String.format;
import static java.util.Optional.of;
import static java.util.UUID.fromString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.notification.notify.event.processor.download.DocumentDownloadClient;
import uk.gov.moj.cpp.notification.notify.event.processor.download.SuccessfulDocumentDownload;
import uk.gov.moj.cpp.notification.notify.event.processor.download.UnsuccessfulDocumentDownload;
import uk.gov.moj.cpp.notification.notify.event.processor.response.DownloadResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.SendEmailDetails;

import java.io.ByteArrayInputStream;
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
public class EmailMaterialDownloaderTest {
    @Mock
    private Logger logger;

    @Mock
    private DocumentDownloadClient documentDownloadClient;

    @InjectMocks
    private EmailMaterialDownloader emailMaterialDownloader;

    @Test
    public void shouldReturnAnErrorIfTheEmailFileLinkCannotBeDownloaded() throws Exception {

        final String materialUrl = "document url";
        final UUID notificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final int statusCode = 500;
        final UnsuccessfulDocumentDownload unsuccessfulDocumentDownload = new UnsuccessfulDocumentDownload(statusCode, "It failed");
        final SendEmailDetails sendEmailDetails = mock(SendEmailDetails.class);
        when(sendEmailDetails.getMaterialUrl()).thenReturn(of(materialUrl));
        when(documentDownloadClient.getDocument(materialUrl)).thenReturn(unsuccessfulDocumentDownload);
        when(sendEmailDetails.getMaterialUrl()).thenReturn(of(materialUrl));
        when(logger.isInfoEnabled()).thenReturn(true);

        final ErrorResponse errorResponse = (ErrorResponse) emailMaterialDownloader.downloadMaterial(notificationId, sendEmailDetails);
        final InOrder inOrder = inOrder(logger);
        inOrder.verify(logger).info(format("Email attachment Download failed for Notification: %s", notificationId));

        assertThat(errorResponse.getStatusCode(), is(statusCode));
        assertThat(errorResponse.getErrorMessage(), is("Failed to download. Error message: 'It failed'"));
    }

    @Test
    public void shouldReturnDownloadRespnseIfTheEmailFileLinkCanBeDownloaded() throws Exception {

        final String materialUrl = "document url";
        final UUID notificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final int contentSize = 50;
        final byte[] documentContent = "content".getBytes();

        final InputStream emailInputStream = new ByteArrayInputStream(documentContent);
        final SuccessfulDocumentDownload successfulDocumentDownload = new SuccessfulDocumentDownload(0, emailInputStream, contentSize, documentContent, "sampleFile.pdf");
        final SendEmailDetails sendEmailDetails = mock(SendEmailDetails.class);
        when(sendEmailDetails.getMaterialUrl()).thenReturn(of(materialUrl));
        when(documentDownloadClient.getDocument(materialUrl)).thenReturn(successfulDocumentDownload);
        when(sendEmailDetails.getMaterialUrl()).thenReturn(of(materialUrl));
        when(logger.isInfoEnabled()).thenReturn(true);

        final DownloadResponse downloadResponse = (DownloadResponse) emailMaterialDownloader.downloadMaterial(notificationId, sendEmailDetails);
        final InOrder inOrder = inOrder(logger);
        inOrder.verify(logger).info("Email Download successful for Notification:{}, with download content size of {}", notificationId, contentSize);

        assertThat(downloadResponse.isSuccessful(), is(true));
        assertThat(downloadResponse.getSuccessfulDocumentDownload(), is(successfulDocumentDownload));
    }
}