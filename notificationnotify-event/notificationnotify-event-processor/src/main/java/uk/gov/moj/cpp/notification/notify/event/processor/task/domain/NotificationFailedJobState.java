package uk.gov.moj.cpp.notification.notify.event.processor.task.domain;

import java.util.UUID;

public class NotificationFailedJobState extends NotificationJobState<NotificationFailedDetails> {

    public NotificationFailedJobState(
            final UUID notificationId,
            final NotificationFailedDetails taskPayload) {
        super(notificationId, taskPayload);
    }
}
