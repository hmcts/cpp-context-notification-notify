package uk.gov.moj.cpp.notification.notify.event.processor.task.handlers;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static javax.mail.Flags.Flag.DELETED;
import static org.slf4j.LoggerFactory.getLogger;

import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.EmailDetail;

import java.util.Properties;

import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;

import org.slf4j.Logger;

public class BaseEmailHandler implements AutoCloseable {
    private static final Logger LOGGER = getLogger(BaseEmailHandler.class);

    protected static final String INBOX_FOLDER = "INBOX";
    protected static final String JUNK_EMAIL_FOLDER = "Junk Email";
    protected static final String ARCHIVE_FOLDER = "Archive";
    protected static final String IMAP_PROTOCOL = "imap";
    protected static final String MULTIPART_CONTENT_TYPE = "multipart";
    protected static final String MESSAGE_RFC_822_CONTENT_TYPE = "message/rfc822";
    protected static final String NOTIFICATION_ID = "NotificationId";
    protected static final String MULTIPART_REPORT = "multipart/report";

    protected Store store;

    public void configureStore(final String user, final String password, final String host, final int port) throws MessagingException {
        if (isNull(store)) {
            final Properties properties = new Properties();
            final Session emailSession = Session.getDefaultInstance(properties);
            store = emailSession.getStore(IMAP_PROTOCOL);
            store.connect(host, port, user, password);
            LOGGER.info("BaseEmailHandler store connection established");
        }
    }


    public void deleteEmail(final EmailDetail emailDetail) throws MessagingException {
        final Message[] messages = new Message[]{emailDetail.getMessage()};
        final Folder emailFolder = emailDetail.getMessage().getFolder();
        emailFolder.setFlags(messages, new Flags(DELETED), true);
        emailFolder.expunge();
    }
    public void moveEmailToArchive(final EmailDetail emailDetail) throws MessagingException {
        final Folder archiveFolder = store.getFolder(ARCHIVE_FOLDER);
        final Folder emailFolder = emailDetail.getMessage().getFolder();
        try {
            archiveFolder.open(Folder.READ_WRITE);
            final Message[] messages = new Message[]{emailDetail.getMessage()};
            emailFolder.copyMessages(messages, archiveFolder);
            emailFolder.setFlags(messages, new Flags(DELETED), true);
            emailFolder.expunge();
        } finally {
            if (emailFolder.isOpen()) {
                emailFolder.close(false);
            }
            if (archiveFolder.isOpen()) {
                archiveFolder.close(false);
            }
        }
    }

    protected boolean isMultipartMessage(Message message) throws MessagingException {
        return message.getContentType().contains(MULTIPART_CONTENT_TYPE);
    }

    @Override
    public void close() {
        if (nonNull(store)) {
            try {
                store.close();
            } catch (MessagingException e) {
                // ignore
                LOGGER.error("Exception occurred while closing store", e);
            }
        }
    }
}
