package uk.gov.moj.cpp.notification.notify.event.processor.client;

import static com.google.common.collect.Sets.newHashSet;

import java.util.Set;

public enum NotificationStatus {

    ACCEPTED("accepted"),
    CREATED("created"),
    SENDING("sending"),
    DELIVERED("delivered"),
    TEMPORARY_FAILURE("temporary-failure"),
    PERMANENT_FAILURE("permanent-failure"),
    FAILED("technical-failure"),
    NOT_FOUND("not found"),
    INVALID_REQUEST("validation-failed"),
    UNEXPECTED_FAILURE("unexpected"),
    RECEIVED("received"),
    PENDING_VIRUS_CHECK("pending-virus-check"),
    VIRUS_SCAN_FAILED("virus-scan-failed");

    private static final Set<NotificationStatus> FAILURE_STATES = newHashSet(
            FAILED,
            VIRUS_SCAN_FAILED,
            PERMANENT_FAILURE);

    private final String status;

    NotificationStatus(String status) {
        this.status = status;
    }

    public static NotificationStatus fromStatus(final String status) {
        for (final NotificationStatus value : values()) {
            if (value.status.equals(status)) {
                return value;
            }
        }
        return UNEXPECTED_FAILURE;
    }

    public String getStatus() {
        return status;
    }

    public boolean isInProgress() {
        return !isFailed();
    }

    public boolean isFailed() {
        return FAILURE_STATES.contains(this);
    }

    public boolean isInvalidRequest() {
        return INVALID_REQUEST.equals(this);
    }

}
