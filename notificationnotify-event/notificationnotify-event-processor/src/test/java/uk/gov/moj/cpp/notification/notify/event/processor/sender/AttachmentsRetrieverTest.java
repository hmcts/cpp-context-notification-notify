package uk.gov.moj.cpp.notification.notify.event.processor.sender;

import static java.util.Optional.empty;
import static java.util.UUID.randomUUID;
import static javax.json.Json.createObjectBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

import uk.gov.justice.services.fileservice.api.FileRetriever;
import uk.gov.justice.services.fileservice.api.FileServiceException;
import uk.gov.justice.services.fileservice.domain.FileReference;
import uk.gov.moj.cpp.notification.notify.event.processor.response.DownloadResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.NotificationResponse;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.UUID;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
public class AttachmentsRetrieverTest {

    @Mock
    private Logger logger;

    @Mock
    private FileRetriever fileRetriever;

    @InjectMocks
    private AttachmentsRetriever attachmentsRetriever;

    @Test
    public void shouldGetAttachmentFromFileService() throws FileServiceException {
        final UUID notificationId = randomUUID();
        final UUID fileId = randomUUID();

        final byte[] content = "Content".getBytes();
        final String filename = "sample.pdf";
        when(fileRetriever.retrieve(fileId)).thenReturn(Optional.of(new FileReference(fileId, createObjectBuilder().add("fileName", filename).build(), new ByteArrayInputStream(content))));
        final NotificationResponse attachment = attachmentsRetriever.getAttachment(notificationId, fileId);

        assertThat(attachment.isSuccessful(), is(true));
        assertThat(attachment, is(instanceOf(DownloadResponse.class)));
        final DownloadResponse downloadResponse = (DownloadResponse) attachment;
        assertThat(downloadResponse.getSuccessfulDocumentDownload().getFileName(), is(filename));
        assertThat(downloadResponse.getSuccessfulDocumentDownload().getBytes(), is(content));
        assertThat(downloadResponse.getSuccessfulDocumentDownload().getHttpResult(), is(HttpStatus.SC_OK));
    }

    @Test
    public void shouldReturnNotFoundWhenFileDoesNotExistInFileService() throws FileServiceException {
        final UUID notificationId = randomUUID();
        final UUID fileId = randomUUID();

        when(fileRetriever.retrieve(fileId)).thenReturn(empty());
        final NotificationResponse attachment = attachmentsRetriever.getAttachment(notificationId, fileId);

        assertThat(attachment.isSuccessful(), is(false));
        assertThat(attachment, is(instanceOf(ErrorResponse.class)));
        final ErrorResponse errorResponse = (ErrorResponse) attachment;
        assertThat(errorResponse.getStatusCode(), is(HttpStatus.SC_NOT_FOUND));
        assertThat(errorResponse.getErrorMessage(), is(String.format("File attachment with id '%s' not found in File Service for notification: %s", fileId, notificationId)));
    }

    @Test
    public void shouldReturnErrorWhenExceptionRetrievingFileFromFileService() throws FileServiceException {
        final UUID notificationId = randomUUID();
        final UUID fileId = randomUUID();

        when(fileRetriever.retrieve(fileId)).thenThrow(new FileServiceException("error"));
        final NotificationResponse attachment = attachmentsRetriever.getAttachment(notificationId, fileId);

        assertFalse(attachment.isSuccessful());
        assertThat(attachment, is(instanceOf(ErrorResponse.class)));
        final ErrorResponse errorResponse = (ErrorResponse) attachment;
        assertThat(errorResponse.getStatusCode(), is(999));
        assertThat(errorResponse.getErrorMessage(), is(String.format("Failed to retrieve file attachment with id '%s' from File Service for notification: %s", fileId, notificationId)));
    }

}