package uk.gov.moj.cpp.notification.notify.event.processor.sender;

import static java.lang.String.format;
import static java.util.Arrays.copyOfRange;

import uk.gov.moj.cpp.notification.notify.filestore.azure.AzureBlobConfiguration;

import java.net.URI;
import java.util.Properties;
import java.util.UUID;

import javax.activation.DataHandler;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobRange;

import java.io.ByteArrayOutputStream;

import uk.gov.justice.services.common.configuration.Value;

import org.slf4j.Logger;

@ApplicationScoped
public class BlobFileEmailSender {

    private static final long MAX_BLOB_SIZE_BYTES = 1_000_000_000L;
    private static final String CONTENT_TYPE_OCTET_STREAM = "application/octet-stream";

    @Inject
    @Value(key = "notify.blob.email.smtp.host", defaultValue = "localhost")
    private String smtpHost;

    @Inject
    @Value(key = "notify.blob.email.smtp.port", defaultValue = "25")
    private String smtpPort;

    @Inject
    @Value(key = "notify.blob.email.from.address", defaultValue = "noreply@noreply.com")
    private String fromAddress;

    @Inject
    private AzureBlobConfiguration azureBlobConfiguration;

    @Inject
    @SuppressWarnings("squid:S1312")
    private Logger logger;

    public void sendEmailWithBlobAttachment(final UUID correlationId,
                                            final String sourceBlobUri,
                                            final String recipientEmail,
                                            final String subject,
                                            final String filename) {
        logger.info("Streaming blob to email attachment correlationId='{}' sourceBlobUri='{}'",
                correlationId, sourceBlobUri);

        final BlobClient blobClient = buildBlobClient(sourceBlobUri);

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        blobClient.downloadStreamWithResponse(
                outputStream,
                new BlobRange(0, MAX_BLOB_SIZE_BYTES),
                null, null, false, null, null);

        final byte[] bytes = outputStream.toByteArray();

        logger.info("Sending email with blob attachment correlationId='{}' filename='{}' bytes={}",
                correlationId, filename, bytes.length);

        try {
            sendViaSmtp(correlationId, recipientEmail, subject, filename, bytes);
        } catch (final MessagingException e) {
            throw new RuntimeException(
                    format("Failed to send blob file email correlationId='%s': %s", correlationId, e.getMessage()), e);
        }
    }

    protected void sendViaSmtp(final UUID correlationId,
                              final String recipientEmail,
                              final String subject,
                              final String filename,
                              final byte[] bytes) throws MessagingException {
        final Properties mailProperties = new Properties();
        mailProperties.put("mail.smtp.host", smtpHost);
        mailProperties.put("mail.smtp.port", smtpPort);

        final Session session = Session.getInstance(mailProperties);
        final MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(fromAddress));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
        message.setSubject(subject);
        message.addHeader("X-Correlation-Id", correlationId.toString());

        final MimeBodyPart attachmentPart = new MimeBodyPart();
        attachmentPart.setDataHandler(new DataHandler(new ByteArrayDataSource(bytes, CONTENT_TYPE_OCTET_STREAM)));
        attachmentPart.setFileName(filename);

        final MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(attachmentPart);
        message.setContent(multipart);

        Transport.send(message);
    }

    protected BlobClient buildBlobClient(final String sourceBlobUri) {
        if (azureBlobConfiguration.hasConnectionString()) {
            return buildBlobClientFromConnectionString(
                    azureBlobConfiguration.getConnectionString(), sourceBlobUri);
        }
        return new BlobClientBuilder()
                .endpoint(sourceBlobUri)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
    }

    private static BlobClient buildBlobClientFromConnectionString(final String connectionString,
                                                                   final String sourceBlobUri) {
        final URI uri = URI.create(sourceBlobUri);
        final String[] segments = uri.getPath().split("/");
        // Azurite path: /devstoreaccount1/<container>/<blobPath...>
        // segments[0]="" segments[1]="devstoreaccount1" segments[2]="<container>" segments[3+]="<blobPath>"
        final String containerName = segments[2];
        final String blobName = String.join("/", copyOfRange(segments, 3, segments.length));
        return new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient()
                .getBlobContainerClient(containerName)
                .getBlobClient(blobName);
    }
}
