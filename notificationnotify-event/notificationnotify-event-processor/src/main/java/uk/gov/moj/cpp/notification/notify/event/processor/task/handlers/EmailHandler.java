package uk.gov.moj.cpp.notification.notify.event.processor.task.handlers;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.ArrayUtils.isNotEmpty;

import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.EmailDetail;

import java.io.IOException;
import java.util.UUID;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;

public class EmailHandler extends BaseEmailHandler {

    @SuppressWarnings("squid:S1160")
    public EmailDetail fetchEMailDetails() throws MessagingException, IOException {
        final Folder inboxFolder = store.getFolder(INBOX_FOLDER);
        EmailDetail emailDetail = getMailsFromFolder(inboxFolder);
        if (isNull(emailDetail)) {
            inboxFolder.close(false);
            final Folder junkEmailFolder = store.getFolder(JUNK_EMAIL_FOLDER);
            emailDetail = getMailsFromFolder(junkEmailFolder);
        }

        return emailDetail;
    }


    private EmailDetail getMailsFromFolder(Folder folder) throws MessagingException, IOException {
        folder.open(Folder.READ_WRITE);
        final Message[] messages = folder.getMessages();
        if (isNotEmpty(messages)) {
            final Message message = messages[messages.length - 1];
            final EmailDetail bouncedEmailDetail = getBouncedEmailDetails(message);
            if (nonNull(bouncedEmailDetail)) {
                return bouncedEmailDetail;
            }
            return new EmailDetail(message);
        }
        return null;
    }


    private EmailDetail getBouncedEmailDetails(Message message) throws MessagingException, IOException {
        if (isMultipartMessage(message)) {
            final Multipart multiPart = (Multipart) message.getContent();
            for (int multipartCount = 0; multipartCount < multiPart.getCount(); multipartCount++) {
                final MimeBodyPart part = (MimeBodyPart) multiPart.getBodyPart(multipartCount);
                final UUID notificationId = getNotificationIdFromHeader(part);
                if (nonNull(notificationId)) {
                    return new EmailDetail(message, notificationId);
                }
            }
        }
        return null;
    }

    @SuppressWarnings("squid:S134")
    private UUID getNotificationIdFromHeader(final MimeBodyPart part) throws MessagingException, IOException {

        if (part.getContentType().toLowerCase().contains(MESSAGE_RFC_822_CONTENT_TYPE)) {
            final Part content = (Part) part.getContent();
            final String[] notificationIdHeader = content.getHeader(NOTIFICATION_ID);
            if (nonNull(notificationIdHeader)) {
                return UUID.fromString(notificationIdHeader[0]);
            }// bounced email details can be in the attached document
            else if (content.getContentType().contains(MULTIPART_REPORT)) {
                final MimeMultipart mimeMultipart = (MimeMultipart) content.getContent();
                for (int count = 0; count < mimeMultipart.getCount(); count++) {
                    final UUID notificationId = getNotificationIdFromBodyPart(mimeMultipart.getBodyPart(count));
                    if (nonNull(notificationId)) {
                        return notificationId;
                    }
                }
            }
        }
        return null;
    }

    private UUID getNotificationIdFromBodyPart(final Part mimeBodyPart) throws MessagingException, IOException {
        if (mimeBodyPart.getContentType().toLowerCase().contains(MESSAGE_RFC_822_CONTENT_TYPE)) {
            final Part mimeBodyPartContent = (Part) mimeBodyPart.getContent();
            final String[] notificationIdHeader = mimeBodyPartContent.getHeader(NOTIFICATION_ID);
            if (nonNull(notificationIdHeader)) {
                return UUID.fromString(notificationIdHeader[0]);
            }
        }
        return null;
    }
}