package uk.gov.moj.cpp.notification.notify.event.processor.sender;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.notification.notify.event.processor.response.DownloadResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.NotificationResponse;
import uk.gov.moj.cpp.notification.notify.filestore.azure.AzureBlobConfiguration;

import java.io.OutputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.DownloadRetryOptions;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
public class AttachmentsRetrieverTest {

    @Mock
    private Logger logger;

    @Mock
    private BlobContainerClient blobContainerClient;

    @Mock
    private BlobClient blobClient;

    @Mock
    private BlobProperties blobProperties;

    @Mock
    private AzureBlobConfiguration azureBlobConfiguration;

    @InjectMocks
    private AttachmentsRetriever attachmentsRetriever;

    @Test
    public void shouldGetAttachmentFromFileService() throws Exception {
        final UUID notificationId = randomUUID();
        final UUID fileId = randomUUID();
        final byte[] content = "Content".getBytes();
        final String filename = "sample.pdf";
        final Map<String, String> metadata = new HashMap<>();
        metadata.put("filename", filename);

        when(blobContainerClient.getBlobClient("internal/" + fileId)).thenReturn(blobClient);
        when(blobClient.exists()).thenReturn(true);
        when(blobClient.getProperties()).thenReturn(blobProperties);
        when(blobProperties.getBlobSize()).thenReturn((long) content.length);
        when(blobProperties.getMetadata()).thenReturn(metadata);

        when(azureBlobConfiguration.getTransferTimeout()).thenReturn(Duration.ofSeconds(300));
        final ArgumentCaptor<OutputStream> outputStreamCaptor = ArgumentCaptor.forClass(OutputStream.class);
        final ArgumentCaptor<BlobRange> blobRangeCaptor = ArgumentCaptor.forClass(BlobRange.class);
        doAnswer(invocation -> {
            final OutputStream outputStream = invocation.getArgument(0);
            outputStream.write(content);
            return null;
        }).when(blobClient).downloadStreamWithResponse(
                outputStreamCaptor.capture(), blobRangeCaptor.capture(),
                isA(DownloadRetryOptions.class), isNull(), eq(false), eq(Duration.ofSeconds(300)), isNull());

        final NotificationResponse attachment = attachmentsRetriever.getAttachment(notificationId, fileId);

        assertThat(attachment.isSuccessful(), is(true));
        assertThat(attachment, is(instanceOf(DownloadResponse.class)));
        final DownloadResponse downloadResponse = (DownloadResponse) attachment;
        assertThat(downloadResponse.getSuccessfulDocumentDownload().getFileName(), is(filename));
        assertThat(downloadResponse.getSuccessfulDocumentDownload().getBytes(), is(content));
        assertThat(downloadResponse.getSuccessfulDocumentDownload().getHttpResult(), is(HttpStatus.SC_OK));
        assertThat(blobRangeCaptor.getValue().getOffset(), is(0L));
        assertThat(blobRangeCaptor.getValue().getCount(), is(1_000_000_000L));
    }

    @Test
    public void shouldReturnNotFoundWhenFileDoesNotExistInFileService() {
        final UUID notificationId = randomUUID();
        final UUID fileId = randomUUID();

        when(blobContainerClient.getBlobClient("internal/" + fileId)).thenReturn(blobClient);
        when(blobClient.exists()).thenReturn(false);

        final NotificationResponse attachment = attachmentsRetriever.getAttachment(notificationId, fileId);

        assertThat(attachment.isSuccessful(), is(false));
        assertThat(attachment, is(instanceOf(ErrorResponse.class)));
        final ErrorResponse errorResponse = (ErrorResponse) attachment;
        assertThat(errorResponse.getStatusCode(), is(HttpStatus.SC_NOT_FOUND));
        assertThat(errorResponse.getErrorMessage(), is("File attachment with id '" + fileId + "' not found in File Service for notification: " + notificationId));
    }

    @Test
    public void shouldReturnErrorWhenFileSizeExceedsMaximumDownloadSize() {
        final UUID notificationId = randomUUID();
        final UUID fileId = randomUUID();
        final long oversizedFileBytes = 15_728_641L;

        when(blobContainerClient.getBlobClient("internal/" + fileId)).thenReturn(blobClient);
        when(blobClient.exists()).thenReturn(true);
        when(blobClient.getProperties()).thenReturn(blobProperties);
        when(blobProperties.getBlobSize()).thenReturn(oversizedFileBytes);

        final NotificationResponse attachment = attachmentsRetriever.getAttachment(notificationId, fileId);

        assertThat(attachment.isSuccessful(), is(false));
        assertThat(attachment, is(instanceOf(ErrorResponse.class)));
        final ErrorResponse errorResponse = (ErrorResponse) attachment;
        assertThat(errorResponse.getStatusCode(), is(HttpStatus.SC_REQUEST_TOO_LONG));
        assertThat(errorResponse.getErrorMessage(), is(
                "File attachment with id '" + fileId + "' for notification '" + notificationId + "' exceeds maximum download size: " + oversizedFileBytes + " bytes"));
    }

    @Test
    public void shouldReturnErrorWhenExceptionRetrievingFileFromBlobStorage() {
        final UUID notificationId = randomUUID();
        final UUID fileId = randomUUID();

        when(blobContainerClient.getBlobClient("internal/" + fileId)).thenReturn(blobClient);
        doThrow(BlobStorageException.class).when(blobClient).exists();

        final NotificationResponse attachment = attachmentsRetriever.getAttachment(notificationId, fileId);

        assertThat(attachment.isSuccessful(), is(false));
        assertThat(attachment, is(instanceOf(ErrorResponse.class)));
        final ErrorResponse errorResponse = (ErrorResponse) attachment;
        assertThat(errorResponse.getStatusCode(), is(999));
        assertThat(errorResponse.getErrorMessage(), is("Failed to retrieve file attachment with id '" + fileId + "' from File Service for notification: " + notificationId));
    }
}
