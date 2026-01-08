package uk.gov.moj.cpp.notification.notify.event.processor.response;

import uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus;

public class StatusResponse implements NotificationResponse {

    private final NotificationStatus notificationStatus;

    public StatusResponse(final NotificationStatus notificationStatus) {
        this.notificationStatus = notificationStatus;
    }

    @Override
    public boolean isSuccessful() {
        return true;
    }

    public NotificationStatus getNotificationStatus() {
        return notificationStatus;
    }
}
