package uk.gov.moj.cpp.notification.notify.event.processor.sender;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;

import uk.gov.moj.cpp.notification.notify.filestore.azure.AzureBlobConfiguration;

import java.io.OutputStream;
import java.util.UUID;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Transport;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.models.BlobRange;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
public class BlobFileEmailSenderTest {

    // Azurite well-known public development key — not a real secret.
    // See: https://learn.microsoft.com/azure/storage/common/storage-use-azurite
    private static final String AZURITE_CONNECTION_STRING =
            "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;" +
            "AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/" +
            "K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://localhost:10000/devstoreaccount1;";

    @Mock
    private AzureBlobConfiguration azureBlobConfiguration;

    @Mock
    private Logger logger;

    @Mock
    private BlobClient blobClient;

    @InjectMocks
    private BlobFileEmailSender blobFileEmailSender;

    @Test
    public void shouldSendEmailWithBlobAttachmentViaSmtp() throws MessagingException {
        final UUID correlationId = randomUUID();
        final String sourceBlobUri = "http://127.0.0.1:10000/devstoreaccount1/my-container/internal/my-file";
        final String recipient = "recipient@example.com";
        final String subject = "Test email subject";
        final String filename = "attachment.pdf";
        final byte[] content = "pdf content bytes".getBytes();

        final BlobFileEmailSender spied = spy(blobFileEmailSender);
        doReturn(blobClient).when(spied).buildBlobClient(sourceBlobUri);

        final ArgumentCaptor<OutputStream> outputStreamCaptor = ArgumentCaptor.forClass(OutputStream.class);
        final ArgumentCaptor<BlobRange> blobRangeCaptor = ArgumentCaptor.forClass(BlobRange.class);
        doAnswer(invocation -> {
            final OutputStream outputStream = invocation.getArgument(0);
            outputStream.write(content);
            return null;
        }).when(blobClient).downloadStreamWithResponse(
                outputStreamCaptor.capture(), blobRangeCaptor.capture(),
                isNull(), isNull(), eq(false), isNull(), isNull());

        final ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        doNothing().when(spied).sendViaSmtp(
                eq(correlationId), eq(recipient), eq(subject), eq(filename), bytesCaptor.capture());

        spied.sendEmailWithBlobAttachment(correlationId, sourceBlobUri, recipient, subject, filename);

        assertThat(blobRangeCaptor.getValue().getOffset(), is(0L));
        assertThat(blobRangeCaptor.getValue().getCount(), is(1_000_000_000L));
        assertThat(bytesCaptor.getValue(), is(content));
        verify(spied).sendViaSmtp(eq(correlationId), eq(recipient), eq(subject), eq(filename), eq(content));
    }

    @Test
    public void shouldThrowRuntimeExceptionWhenSmtpFails() throws MessagingException {
        final UUID correlationId = randomUUID();
        final String sourceBlobUri = "http://127.0.0.1:10000/devstoreaccount1/my-container/internal/my-file";
        final String recipient = "to@test.com";
        final String subject = "Subject";
        final String filename = "file.pdf";
        final byte[] content = "pdf content bytes".getBytes();

        final BlobFileEmailSender spied = spy(blobFileEmailSender);
        doReturn(blobClient).when(spied).buildBlobClient(sourceBlobUri);

        final ArgumentCaptor<OutputStream> outputStreamCaptor = ArgumentCaptor.forClass(OutputStream.class);
        final ArgumentCaptor<BlobRange> blobRangeCaptor = ArgumentCaptor.forClass(BlobRange.class);
        doAnswer(invocation -> {
            final OutputStream outputStream = invocation.getArgument(0);
            outputStream.write(content);
            return null;
        }).when(blobClient).downloadStreamWithResponse(
                outputStreamCaptor.capture(), blobRangeCaptor.capture(),
                isNull(), isNull(), eq(false), isNull(), isNull());

        doThrow(new MessagingException("SMTP connection refused"))
                .when(spied).sendViaSmtp(eq(correlationId), eq(recipient), eq(subject), eq(filename), eq(content));

        final RuntimeException exception = assertThrows(RuntimeException.class, () ->
                spied.sendEmailWithBlobAttachment(correlationId, sourceBlobUri, recipient, subject, filename));

        assertThat(exception.getMessage(), is(
                "Failed to send blob file email correlationId='" + correlationId + "': SMTP connection refused"));
    }

    @Test
    public void shouldBuildCorrectSmtpMessageInSendViaSmtp() throws Exception {
        setField(blobFileEmailSender, "smtpHost", "localhost");
        setField(blobFileEmailSender, "smtpPort", "25");
        setField(blobFileEmailSender, "fromAddress", "noreply@noreply.com");

        final UUID correlationId = randomUUID();
        final String recipient = "recipient@example.com";
        final String subject = "Test email subject";
        final String filename = "attachment.pdf";
        final byte[] bytes = "pdf content bytes".getBytes();

        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        try (final MockedStatic<Transport> mockedTransport = mockStatic(Transport.class)) {
            blobFileEmailSender.sendViaSmtp(correlationId, recipient, subject, filename, bytes);
            mockedTransport.verify(() -> Transport.send(messageCaptor.capture()));
        }

        final Message message = messageCaptor.getValue();
        assertThat(message.getSubject(), is(subject));
        assertThat(message.getRecipients(Message.RecipientType.TO)[0].toString(), is(recipient));
        assertThat(message.getHeader("X-Correlation-Id")[0], is(correlationId.toString()));
    }

    @Test
    public void shouldBuildBlobClientFromConnectionString() {
        when(azureBlobConfiguration.hasConnectionString()).thenReturn(true);
        when(azureBlobConfiguration.getConnectionString()).thenReturn(AZURITE_CONNECTION_STRING);

        final BlobClient result = blobFileEmailSender.buildBlobClient(
                "http://127.0.0.1:10000/devstoreaccount1/my-container/internal/some-file");

        assertThat(result, is(notNullValue()));
        assertThat(result.getBlobName(), is("internal/some-file"));
    }

    @Test
    public void shouldBuildBlobClientUsingDefaultAzureCredential() {
        when(azureBlobConfiguration.hasConnectionString()).thenReturn(false);

        final BlobClient result = blobFileEmailSender.buildBlobClient(
                "https://devstoreaccount1.blob.core.windows.net/my-container/internal/some-file");

        assertThat(result, is(notNullValue()));
    }
}
