package uk.gov.moj.cpp.notification.notify.event.processor.retry;

public class NotifyConfigurationException extends RuntimeException {

    public NotifyConfigurationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
