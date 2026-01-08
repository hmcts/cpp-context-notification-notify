package uk.gov.moj.cpp.notification.factory;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import uk.gov.moj.cpp.notification.entity.Notification;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.junit.Test;

public class NotificationFactoryTest {

    @Test
    public void shouldCreateEmailTypeNotification() {
        final UUID notificationId = randomUUID();
        final String status = "status";
        final String sendToAddress = "send to address";
        final String emailUrl = "http:linkToDownload";
        final ZonedDateTime dateCreated = ZonedDateTime.now();
        final ZonedDateTime lastUpdated = ZonedDateTime.now();

        final Notification emailNotification = new NotificationFactory().createEmailNotification(
                notificationId,
                status,
                emailUrl,
                sendToAddress,
                dateCreated,
                lastUpdated);

        assertThat(emailNotification.getNotificationId(), is(notificationId));
        assertThat(emailNotification.getStatus(), is(status));
        assertThat(emailNotification.getSendToAddress(), is(sendToAddress));
        assertThat(emailNotification.getDateCreated(), is(dateCreated));
        assertThat(emailNotification.getLastUpdated(), is(lastUpdated));
        assertThat(emailNotification.getLetterUrl(), is(nullValue()));
        assertThat(emailNotification.getMaterialUrl(), is(emailUrl));

    }

    @Test
    public void shouldCreateLetterTypeNotification() {
        final UUID notificationId = randomUUID();
        final String status = "status";
        final String letterUrl = "http://someplace.co.uk/letter.pdf";
        final ZonedDateTime dateCreated = ZonedDateTime.now();
        final ZonedDateTime lastUpdated = ZonedDateTime.now();

        final Notification emailNotification = new NotificationFactory().createLetterNotification(
                notificationId,
                status,
                letterUrl,
                dateCreated,
                lastUpdated);

        assertThat(emailNotification.getNotificationId(), is(notificationId));
        assertThat(emailNotification.getStatus(), is(status));
        assertThat(emailNotification.getLetterUrl(), is(letterUrl));
        assertThat(emailNotification.getDateCreated(), is(dateCreated));
        assertThat(emailNotification.getLastUpdated(), is(lastUpdated));
        assertThat(emailNotification.getSendToAddress(), is(nullValue()));
    }
}