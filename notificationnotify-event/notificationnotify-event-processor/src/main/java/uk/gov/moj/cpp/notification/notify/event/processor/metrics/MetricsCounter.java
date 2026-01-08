package uk.gov.moj.cpp.notification.notify.event.processor.metrics;

public enum MetricsCounter {

    EMAIL_SENT_COUNTER("emailSentCounter"),
    EMAIL_SUCCESS_COUNTER("emailSuccessCounter"),
    EMAIL_PERMANENT_FAILURE_COUNTER("emailPermanentFailureCounter"),
    EMAIL_TEMPORARY_FAILURE_COUNTER("emailTemporaryFailureCounter"),
    LETTER_SENT_COUNTER("letterSentCounter"),
    LETTER_SUCCESS_COUNTER("letterSuccessCounter"),
    LETTER_PERMANENT_FAILURE_COUNTER("letterPermanentFailureCounter"),
    LETTER_TEMPORARY_FAILURE_COUNTER("letterTemporaryFailureCounter"),
    LETTER_FAILED_TO_DOWNLOAD_FILE_COUNTER("letterFailedToDownloadFileCounter");

    private final String name;

    MetricsCounter(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
