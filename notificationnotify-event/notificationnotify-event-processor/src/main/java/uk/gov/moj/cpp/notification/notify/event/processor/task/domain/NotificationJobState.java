package uk.gov.moj.cpp.notification.notify.event.processor.task.domain;

import java.util.UUID;

public class NotificationJobState<T> {

    private final UUID notificationId;
    private final T taskPayload;

    public NotificationJobState(
            final UUID notificationId,
            final T taskPayload) {
        this.notificationId = notificationId;
        this.taskPayload = taskPayload;
    }

    public UUID getNotificationId() {
        return notificationId;
    }

    public T getTaskPayload() {
        return taskPayload;
    }
}
