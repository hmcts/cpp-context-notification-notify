package uk.gov.moj.cpp.notification.repository;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.moj.cpp.notification.entity.NotificationType.EMAIL;
import static uk.gov.moj.cpp.notification.entity.NotificationType.LETTER;

import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.test.utils.persistence.BaseTransactionalJunit4Test;
import uk.gov.moj.cpp.notification.entity.Notification;
import uk.gov.moj.cpp.notification.factory.NotificationFactory;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(CdiTestRunner.class)
public class NotificationRepositoryTest extends BaseTransactionalJunit4Test {

    @Inject
    private NotificationRepository notificationRepository;

    @Test
    public void shouldGetEmailWithLinkNotificationById() {

        final UUID notificationId = randomUUID();
        final ZonedDateTime timestamp = new UtcClock().now();
        final String sendToAddress = "sendto@address.com";
        final int statusCode = 202;
        final String errorMessage = "error";
        final String status = "queued";
        final String materialUrl = "http://linkToDownload";

        final Notification notification = createEmailNotificationWith(notificationId, status, materialUrl, sendToAddress, errorMessage, statusCode, timestamp, timestamp);

        notificationRepository.save(notification);

        Notification result = notificationRepository.findBy(notificationId);

        assertThat(result, is(notNullValue()));

        assertThat(result.getNotificationId(), is(notificationId));
        assertThat(result.getNotificationType(), is(EMAIL.name()));
        assertThat(result.getSendToAddress(), is(sendToAddress));
        assertThat(result.getStatus(), is(status));
        assertThat(result.getMaterialUrl(), is(materialUrl));
        assertThat(result.getStatusCode(), is(statusCode));
        assertThat(result.getErrorMessage(), is(errorMessage));
        assertThat(result.getDateCreated(), is(timestamp));
        assertThat(result.getLastUpdated(), is(timestamp));
    }

    @Test
    public void shouldGetEmailWithNoLinkNotificationById() {

        final UUID notificationId = randomUUID();
        final ZonedDateTime timestamp = new UtcClock().now();
        final String sendToAddress = "sendto@address.com";
        final int statusCode = 202;
        final String errorMessage = "error";
        final String status = "queued";
        final String materialUrl = null;

        final Notification notification = createEmailNotificationWith(notificationId, status, materialUrl, sendToAddress, errorMessage, statusCode, timestamp, timestamp);

        notificationRepository.save(notification);

        Notification result = notificationRepository.findBy(notificationId);

        assertThat(result, is(notNullValue()));

        assertThat(result.getNotificationId(), is(notificationId));
        assertThat(result.getNotificationType(), is(EMAIL.name()));
        assertThat(result.getSendToAddress(), is(sendToAddress));
        assertThat(result.getStatus(), is(status));
        assertThat(result.getMaterialUrl(), is(nullValue()));
        assertThat(result.getStatusCode(), is(statusCode));
        assertThat(result.getErrorMessage(), is(errorMessage));
        assertThat(result.getDateCreated(), is(timestamp));
        assertThat(result.getLastUpdated(), is(timestamp));
    }

    @Test
    public void shouldGetLetterNotificationById() {

        final UUID notificationId = randomUUID();
        final ZonedDateTime timestamp = new UtcClock().now();
        final String status = "queued";
        final String letterUrl= "http://localhost";

        final Notification notification = createLetterNotificationWith(notificationId, status, letterUrl, timestamp, timestamp);

        notificationRepository.save(notification);

        Notification result = notificationRepository.findBy(notificationId);

        assertThat(result, is(notNullValue()));

        assertThat(result.getNotificationId(), is(notificationId));
        assertThat(result.getNotificationType(), is(LETTER.name()));
        assertThat(result.getStatus(), is(status));
        assertThat(result.getLetterUrl(), is(letterUrl));
        assertThat(result.getDateCreated(), is(timestamp));
        assertThat(result.getLastUpdated(), is(timestamp));
    }

    @Test
    public void shouldGetEmailNotificationByQueryParams() {
        final UUID notificationId = randomUUID();
        final UUID notificationIdB = randomUUID();
        final UUID notificationIdC = randomUUID();
        final ZonedDateTime timestamp = new UtcClock().now();
        final String sendToAddress = "sendto@address.com";
        final int statusCode = 202;
        final String errorMessage = "error";
        final String status = "queued";
        final String materialUrl = "http://linkToDownload";

        final Notification notification = createEmailNotificationWith(notificationId, status, materialUrl, sendToAddress, errorMessage, statusCode, timestamp, timestamp);
        final Notification notificationB = createEmailNotificationWith(notificationIdB, status, materialUrl, sendToAddress, errorMessage, statusCode, timestamp, timestamp);

        notificationRepository.save(notification);
        notificationRepository.save(notificationB);

        final Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("status", status);
        queryMap.put("createdAfter", timestamp);

        assertThat(notificationRepository.findNotifications(queryMap).size(), is(2));

        // Add a third notification with an earlier creation date, that shouldn't be found by the created after query
        final Notification notificationC = createEmailNotificationWith(notificationIdC, status, materialUrl, sendToAddress, errorMessage, statusCode, timestamp.minusSeconds(5), timestamp);
        notificationRepository.save(notificationC);

        assertThat(notificationRepository.findNotifications(queryMap).size(), is(2));
    }

    @Test
    public void shouldGetLetterNotificationByQueryParams() {
        final UUID notificationId = randomUUID();
        final UUID notificationIdB = randomUUID();
        final UUID notificationIdC = randomUUID();
        final ZonedDateTime timestamp = new UtcClock().now();
        final String status = "queued";
        final String letterUrl = "http://localhost";

        final Notification notification = createLetterNotificationWith(notificationId, status, letterUrl, timestamp, timestamp);
        final Notification notificationB = createLetterNotificationWith(notificationIdB, status, letterUrl, timestamp, timestamp);

        notificationRepository.save(notification);
        notificationRepository.save(notificationB);

        final Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("status", status);
        queryMap.put("notificationType", LETTER.name());
        queryMap.put("createdAfter", timestamp);

        assertThat(notificationRepository.findNotifications(queryMap).size(), is(2));

        // Add a third notification with an earlier creation date, that shouldn't be found by the created after query
        final Notification notificationC = createLetterNotificationWith(notificationIdC, status, letterUrl, timestamp.minusSeconds(5), timestamp);
        notificationRepository.save(notificationC);

        assertThat(notificationRepository.findNotifications(queryMap).size(), is(2));
    }

    @Test
    public void shouldGetLetterNotificationByLetterUrlUsingQueryParams() {
        final UUID notificationId = randomUUID();
        final UUID notificationIdB = randomUUID();
        final UUID notificationIdC = randomUUID();
        final ZonedDateTime timestamp = new UtcClock().now();
        final String status = "queued";
        final String letterUrl = "http://localhost";

        final Notification notification = createLetterNotificationWith(notificationId, status, letterUrl, timestamp, timestamp);
        final Notification notificationB = createLetterNotificationWith(notificationIdB, status, letterUrl, timestamp, timestamp);

        notificationRepository.save(notification);
        notificationRepository.save(notificationB);

        final Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("status", status);
        queryMap.put("letterUrl", letterUrl);

        assertThat(notificationRepository.findNotifications(queryMap).size(), is(2));

        // Add a third notification with different letterUrl
        final Notification notificationC = createLetterNotificationWith(notificationIdC, status, "http://admin", timestamp, timestamp);
        notificationRepository.save(notificationC);

        assertThat(notificationRepository.findNotifications(queryMap).size(), is(2));
    }

    private Notification createEmailNotificationWith(final UUID notificationId,
                                                     final String status,
                                                     final String emailUrl,
                                                     final String sendToAddress,
                                                     final String errorMessage,
                                                     final int statusCode,
                                                     final ZonedDateTime dateCreated,
                                                     final ZonedDateTime lastUpdated) {
        final Notification notification = new NotificationFactory().createEmailNotification(notificationId, status, emailUrl, sendToAddress, dateCreated, lastUpdated);

        notification.setErrorMessage(errorMessage);
        notification.setStatusCode(statusCode);
        return notification;
    }

    private Notification createLetterNotificationWith(final UUID notificationId,
                                                final String status,
                                                final String letterUrl,
                                                final ZonedDateTime dateCreated,
                                                final ZonedDateTime lastUpdated) {
        return new NotificationFactory().createLetterNotification(notificationId, status, letterUrl, dateCreated, lastUpdated);

    }

}