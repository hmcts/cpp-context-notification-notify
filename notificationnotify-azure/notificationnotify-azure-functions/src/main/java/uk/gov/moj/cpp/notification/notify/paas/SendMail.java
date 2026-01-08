package uk.gov.moj.cpp.notification.notify.paas;


import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Base64.getDecoder;
import static java.util.Objects.nonNull;
import static uk.gov.moj.cpp.notification.notify.paas.EnvironmentHelper.MAIL_SMTP_SSL_PROTOCOLS;
import static uk.gov.moj.cpp.notification.notify.paas.EnvironmentHelper.getEnvironmentVariableForMailSmtpSslProtocols;
import static uk.gov.moj.cpp.notification.notify.paas.EnvironmentHelper.getVariable;

import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Logger;

import javax.activation.DataHandler;
import javax.activation.MimetypesFileTypeMap;
import javax.mail.Authenticator;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import com.microsoft.azure.functions.ExecutionContext;

@SuppressWarnings("squid:S1312")
public class SendMail {

    static final String NOTIFICATION_ID = "NotificationId";
    private static final MimetypesFileTypeMap fileTypeMap = new MimetypesFileTypeMap();

    static {
        fileTypeMap.addMimeTypes("application/pdf pdf PDF");
    }

    private SendMail() {
    }

    @SuppressWarnings({"squid:S1160","squid:S107","squid:S2629"})
    private static void sendMail(final Session session, final String toEmail, final String subject, final Attachment attachment,
                                 final String body, final String from, final ExecutionContext context, final String reference) throws MessagingException, UnsupportedEncodingException {
        final Logger logger = context.getLogger();
        final MimeMessage msg = new MimeMessage(session);
        msg.addHeader("Content-type", "text/HTML; charset=UTF-8");
        msg.addHeader("format", "flowed");
        msg.addHeader("Content-Transfer-Encoding", "8bit");
        msg.addHeader(NOTIFICATION_ID, reference);
        msg.setFrom(new InternetAddress(from, "NoReply-JD"));
        msg.setReplyTo(InternetAddress.parse("no_reply@HMCTS.NET", false));
        msg.setSubject(subject, "UTF-8");
        msg.setSentDate(new Date());
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail, false));

        if (nonNull(attachment) && !isNullOrEmpty(attachment.getContent())) {
            prepareWithAttachment(attachment, body, logger, msg);
        } else {
            prepareWithoutAttachment(body, logger, msg);
        }

        logger.info(NOTIFICATION_ID + " " +reference + " Message is ready");
        Transport.send(msg);
        logger.info(NOTIFICATION_ID + " " +reference + "Email Sent Successfully!!");
    }

    private static void prepareWithAttachment(final Attachment attachment,
                                              final String body,
                                              final Logger logger,
                                              final MimeMessage msg) throws MessagingException {
        logger.info("Sending with attachment...");
        final BodyPart bodyPart = new MimeBodyPart();
        bodyPart.setContent(body, "text/html; charset=utf-8");
        final Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(bodyPart);
        final MimeBodyPart attachmentPart = new MimeBodyPart();
        final DataHandler dataHandler = new DataHandler(new ByteArrayDataSource(getDecoder().decode(attachment.getContent()), fileTypeMap.getContentType(attachment.getFilename())));
        attachmentPart.setDataHandler(dataHandler);
        attachmentPart.setFileName(attachment.getFilename());
        multipart.addBodyPart(attachmentPart);
        msg.setContent(multipart);
    }

    private static void prepareWithoutAttachment(String body, Logger logger, MimeMessage msg) throws MessagingException {
        logger.info("Sending without attachment...");
        msg.setContent(body, "text/html; charset=utf-8");
    }

    @SuppressWarnings({"squid:S1172", "squid:S1160"})
    public static void sendMail(
            final String subject,
            final String content,
            final String recipientEmail,
            final Attachment attachment,
            final String reference,
            final OutlookCredentials outlookCredentials,
            final ExecutionContext context
    ) throws MessagingException, UnsupportedEncodingException {

        final Logger logger = context.getLogger();
        logger.info(() -> NOTIFICATION_ID + " " +reference + " Sending email to " + recipientEmail);


        final String username = getVariable(outlookCredentials.getUsername());
        final String server = outlookCredentials.getServer();
        final String port = outlookCredentials.getPort();
        final String password = getVariable(outlookCredentials.getPassword());

        logger.info( () -> NOTIFICATION_ID + " " + reference + " TLSEmail Start");
        final Properties props = new Properties();
        props.put("mail.smtp.host", server);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put(MAIL_SMTP_SSL_PROTOCOLS, getEnvironmentVariableForMailSmtpSslProtocols());

        logger.info(() -> NOTIFICATION_ID + " " +reference + " Sending email to " + server + ":" + port + " for user: " + username);

        final Authenticator auth = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        };
        final Session session = Session.getInstance(props, auth);

        sendMail(session, recipientEmail, subject, attachment, content, username, context, reference);
    }
}