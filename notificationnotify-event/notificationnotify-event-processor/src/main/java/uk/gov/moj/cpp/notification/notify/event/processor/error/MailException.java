package uk.gov.moj.cpp.notification.notify.event.processor.error;

public class MailException extends RuntimeException {

    public MailException(final Throwable cause) {
        super(cause);
    }
}