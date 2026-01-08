package uk.gov.moj.cpp.notification.notify.event.processor.task.processors;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE;

import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;

public class FailureSelector {

    private static final String NOTIFICATION_EXCEPTION_MESSAGE = "Error when turning Base64InputStream into a string";
    private static final String NOTIFICATION_DOWNLOAD_EXCEPTION_MESSAGE = "Document download failed for Notification";

    public boolean isTemporaryFailure(final ErrorResponse errorResponse) {
        return isFullPdfDownloadFailure(errorResponse) || !isPermanentFailure(errorResponse) || isInternalServerError(errorResponse) || isDocumentDownloadFailure(errorResponse);
    }

    private boolean isPermanentFailure(final ErrorResponse errorResponse) {
        return isBadHttpStatusCode(errorResponse) || isEmailFileAttachmentSizeTooBig(errorResponse);
    }

    private boolean isFullPdfDownloadFailure(final ErrorResponse errorResponse) {
        return errorResponse.getStatusCode() == 0 &&
                errorResponse.getErrorMessage() != null &&
                errorResponse.getErrorMessage().contains(NOTIFICATION_EXCEPTION_MESSAGE);
    }

    private boolean isBadHttpStatusCode(final ErrorResponse errorResponse) {
        return (errorResponse.getStatusCode() == 0) ||
                (errorResponse.getStatusCode() == SC_BAD_REQUEST);
    }

    private boolean isEmailFileAttachmentSizeTooBig(final ErrorResponse errorResponse) {
        return (errorResponse.getStatusCode() == SC_REQUEST_ENTITY_TOO_LARGE);
    }

    private boolean isInternalServerError(final ErrorResponse errorResponse) {
        return (errorResponse.getStatusCode() == SC_INTERNAL_SERVER_ERROR);
    }

    private boolean isDocumentDownloadFailure(final ErrorResponse errorResponse) {
        return errorResponse.getStatusCode() == 0 &&
                errorResponse.getErrorMessage() != null &&
                errorResponse.getErrorMessage().contains(NOTIFICATION_DOWNLOAD_EXCEPTION_MESSAGE);
    }
}
