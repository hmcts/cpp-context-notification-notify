package uk.gov.moj.cpp.notification.notify.event.processor.error;

public class XmlProcessingException extends RuntimeException {

    public XmlProcessingException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public XmlProcessingException(final String message) {
        super(message);
    }
}
