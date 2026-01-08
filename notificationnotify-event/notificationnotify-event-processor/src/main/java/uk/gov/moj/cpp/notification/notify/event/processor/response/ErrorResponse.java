package uk.gov.moj.cpp.notification.notify.event.processor.response;

public class ErrorResponse implements NotificationResponse {

    private final String errorMessage;
    private final int statusCode;

    public ErrorResponse(final String errorMessage,
                         final int statusCode) {
        this.errorMessage = errorMessage;
        this.statusCode = statusCode;
    }

    @Override
    public boolean isSuccessful() {
        return false;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
