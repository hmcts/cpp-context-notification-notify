package uk.gov.moj.cpp.notification.notify.event.processor.task.handlers;

import static java.util.UUID.randomUUID;
import static javax.mail.Flags.Flag.DELETED;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.EmailDetail;

import java.io.IOException;
import java.util.UUID;

import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Store;
import javax.mail.internet.MimeBodyPart;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EmailHandlerTest {


    @InjectMocks
    private EmailHandler emailHandler;

    @Mock
    private Store store;

    @Mock
    private Folder inbox;

    @Mock
    private Folder junkEmail;

    @Mock
    private Folder bounced;

    @Mock
    private Message message;

    @Mock
    private Multipart multipart;

    @Mock
    private MimeBodyPart mimeBodyPart;

    @Mock
    private Part part;
    @Captor
    private ArgumentCaptor<Message[]> argumentCaptor;

    @Captor
    private ArgumentCaptor<Folder> argumentCaptorFolder;

    @Captor
    private ArgumentCaptor<Boolean> argumentValueCaptor;

    @Captor
    private ArgumentCaptor<Flags> argumentFlagCaptor;

    @Test
    public void shouldFetchBouncedEmailFromInbox() throws IOException, MessagingException {


        UUID notificationId = randomUUID();
        when(store.getFolder("INBOX")).thenReturn(inbox);
        when(inbox.getMessages()).thenReturn(new Message[]{message});
        when(message.getContentType()).thenReturn("multipart");
        when(message.getContent()).thenReturn(multipart);
        when(multipart.getCount()).thenReturn(1);
        when(multipart.getBodyPart(0)).thenReturn(mimeBodyPart);
        when(mimeBodyPart.getContentType()).thenReturn("message/rfc822");
        when(mimeBodyPart.getContent()).thenReturn(part);
        when(part.getHeader("NotificationId")).thenReturn(new String[]{notificationId.toString()});

        EmailDetail emailDetail = emailHandler.fetchEMailDetails();
        assertThat(emailDetail.getNotificationId(), is(notificationId));
        assertThat(emailDetail.getMessage(), is(message));

    }

    @Test
    public void shouldFetchBouncedEmailFromJunkEmailWhenNoMessagesInInbox() throws IOException, MessagingException {

        UUID notificationId = randomUUID();
        when(store.getFolder("INBOX")).thenReturn(inbox);
        when(inbox.getMessages()).thenReturn(new Message[]{});
        when(store.getFolder("Junk Email")).thenReturn(junkEmail);
        when(junkEmail.getMessages()).thenReturn(new Message[]{message});
        when(message.getContentType()).thenReturn("multipart");
        when(message.getContent()).thenReturn(multipart);
        when(multipart.getCount()).thenReturn(1);
        when(multipart.getBodyPart(0)).thenReturn(mimeBodyPart);
        when(mimeBodyPart.getContentType()).thenReturn("message/rfc822");
        when(mimeBodyPart.getContent()).thenReturn(part);
        when(part.getHeader("NotificationId")).thenReturn(new String[]{notificationId.toString()});

        EmailDetail emailDetail = emailHandler.fetchEMailDetails();
        assertThat(emailDetail.getNotificationId(), is(notificationId));
        assertThat(emailDetail.getMessage(), is(message));

    }

    @Test
    public void shouldMoveBouncedEmailToBouncedFolder() throws MessagingException {
        UUID notificationId = randomUUID();
        EmailDetail emailDetail = new EmailDetail(message, notificationId);
        when(store.getFolder("Archive")).thenReturn(bounced);
        when(message.getFolder()).thenReturn(inbox);

        emailHandler.moveEmailToArchive(emailDetail);

        verify(inbox).copyMessages(argumentCaptor.capture(), argumentCaptorFolder.capture());
        assertThat(argumentCaptor.getValue()[0], is(message));
        assertThat(argumentCaptorFolder.getValue(), is(bounced));
        verify(inbox).setFlags(argumentCaptor.capture(), argumentFlagCaptor.capture(), argumentValueCaptor.capture());
        verify(inbox).expunge();
        assertThat(argumentCaptor.getValue()[0], is(message));
        assertTrue(argumentFlagCaptor.getValue().contains(DELETED));
        assertThat(argumentValueCaptor.getValue(), is(true));
    }


}
