package uk.gov.moj.cpp.notification.notify.event.processor.response;

import uk.gov.moj.cpp.notification.notify.event.processor.download.SuccessfulDocumentDownload;

public class DownloadResponse implements NotificationResponse {

    private final SuccessfulDocumentDownload successfulDocumentDownload;

    public DownloadResponse(final SuccessfulDocumentDownload successfulDocumentDownload) {
        this.successfulDocumentDownload = successfulDocumentDownload;
    }

    @Override
    public boolean isSuccessful() {
        return true;
    }

    public SuccessfulDocumentDownload getSuccessfulDocumentDownload() {
        return successfulDocumentDownload;
    }
}
