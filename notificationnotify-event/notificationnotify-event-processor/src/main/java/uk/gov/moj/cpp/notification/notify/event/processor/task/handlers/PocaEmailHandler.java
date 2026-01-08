package uk.gov.moj.cpp.notification.notify.event.processor.task.handlers;

import static org.slf4j.LoggerFactory.getLogger;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.domain.EmailDetail.emailDetails;

import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.EmailDetail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.mail.Address;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.UIDFolder;
import javax.mail.internet.MimeBodyPart;

import org.slf4j.Logger;

public class PocaEmailHandler extends BaseEmailHandler implements AutoCloseable {

    private static final Logger LOGGER = getLogger(PocaEmailHandler.class);

    @SuppressWarnings("squid:S1160")
    public List<EmailDetail> fetchPocaEmailDetails() throws MessagingException, IOException {
        final Folder inboxFolder = store.getFolder(INBOX_FOLDER);
        return getMailsFromFolder(inboxFolder);
    }


    private List<EmailDetail> getMailsFromFolder(Folder folder) throws MessagingException, IOException {

        final List<EmailDetail> emailDetails = new ArrayList<>();
        folder.open(Folder.READ_WRITE);
        final Message[] emailMessages = folder.getMessages();
        LOGGER.info("PocaEmailHandler message count: {}", emailMessages.length);

        for (final Message message : emailMessages) {
            final UIDFolder uf = (UIDFolder) folder;
            final Long uniqueMessageId = uf.getUID(message);
            final Address[] fromAddress = message.getFrom();
            final String senderEmailAddress = fromAddress[0].toString();
            final String mailSubject = message.getSubject();
            emailDetails.add(getPocaEmailDetails(message, uniqueMessageId, senderEmailAddress, mailSubject));
        }
        return emailDetails;
    }

    private EmailDetail getPocaEmailDetails(Message message, Long uniqueMessageId, final String senderEmail, final String subject) throws MessagingException, IOException {
        if (isMultipartMessage(message)) {
            final Multipart multiPart = (Multipart) message.getContent();
            for (int multipartCount = 0; multipartCount < multiPart.getCount(); multipartCount++) {
                final MimeBodyPart part = (MimeBodyPart) multiPart.getBodyPart(multipartCount);
                if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                    LOGGER.info("Poca mail has an attachment with senderEmail {} and subject {}",senderEmail, subject);
                    return emailDetails()
                            .withMessage(message)
                            .withSenderEmail(senderEmail)
                            .withSubject(subject)
                            .withFileName(part.getFileName())
                            .withPocaMailId(new UUID(message.getReceivedDate().getTime(), uniqueMessageId))
                            .withDocumentContent(part.getInputStream())
                            .build();
                }
            }
        }
        return null;
    }

}
