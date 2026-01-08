package uk.gov.moj.cpp.notification.factory;

import static uk.gov.moj.cpp.notification.entity.NotificationType.EMAIL;
import static uk.gov.moj.cpp.notification.entity.NotificationType.LETTER;

import uk.gov.moj.cpp.notification.entity.Notification;

import java.time.ZonedDateTime;
import java.util.UUID;

public class NotificationFactory {

    public Notification createEmailNotification(final UUID notificationId,
                                                final String status,
                                                final String materialUrl,
                                                final String sendToAddress,
                                                final ZonedDateTime dateCreated,
                                                final ZonedDateTime lastUpdated) {
        final Notification notification = new Notification(notificationId, EMAIL);

        notification.setStatus(status);
        notification.setMaterialUrl(materialUrl);
        notification.setSendToAddress(sendToAddress);
        notification.setDateCreated(dateCreated);
        notification.setLastUpdated(lastUpdated);

        return notification;
    }

    public Notification createLetterNotification(final UUID notificationId,
                                                final String status,
                                                final String letterUrl,
                                                final ZonedDateTime dateCreated,
                                                final ZonedDateTime lastUpdated) {
        final Notification notification = new Notification(notificationId, LETTER);

        notification.setStatus(status);
        notification.setLetterUrl(letterUrl);
        notification.setDateCreated(dateCreated);
        notification.setLastUpdated(lastUpdated);

        return notification;
    }
}
