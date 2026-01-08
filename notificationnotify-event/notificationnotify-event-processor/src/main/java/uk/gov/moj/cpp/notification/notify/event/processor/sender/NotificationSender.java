package uk.gov.moj.cpp.notification.notify.event.processor.sender;

import uk.gov.moj.cpp.notification.notify.event.processor.response.NotificationResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.NotificationJobState;

public interface NotificationSender {

    NotificationResponse send(final NotificationJobState<?> notificationJobState);
}
