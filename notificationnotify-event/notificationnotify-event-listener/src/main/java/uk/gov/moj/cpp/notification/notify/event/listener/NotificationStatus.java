package uk.gov.moj.cpp.notification.notify.event.listener;

public enum NotificationStatus {
    QUEUED,
    REQUEUED,
    ATTEMPTED,
    FAILED,
    SENT,
    URL_FAILED,
    VALIDATION_FAILED
}
