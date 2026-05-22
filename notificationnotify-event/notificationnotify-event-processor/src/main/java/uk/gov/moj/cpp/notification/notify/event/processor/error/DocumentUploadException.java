package uk.gov.moj.cpp.notification.notify.event.processor.error;

public class DocumentUploadException extends RuntimeException {

    public DocumentUploadException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public DocumentUploadException(final String message) {
        super(message);
    }
}
