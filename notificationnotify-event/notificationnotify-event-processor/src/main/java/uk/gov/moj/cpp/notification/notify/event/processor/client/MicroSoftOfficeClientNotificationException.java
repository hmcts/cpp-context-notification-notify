package uk.gov.moj.cpp.notification.notify.event.processor.client;

public class MicroSoftOfficeClientNotificationException extends Exception {
    private static final long serialVersionUID = 2L;
    private final int httpResult;

    public MicroSoftOfficeClientNotificationException(String message) {
        super(message);
        this.httpResult = 400;
    }

    public MicroSoftOfficeClientNotificationException(int httpResult, String message) {
        super("Status code: " + httpResult + " " + message);
        this.httpResult = httpResult;
    }

    public int getHttpResult() {
        return this.httpResult;
    }
}