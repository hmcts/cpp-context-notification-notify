package uk.gov.moj.cpp.notification.notify.event.processor.download;

public class UnsuccessfulDocumentDownload implements DocumentDownloadResponse {

    private final String responseBody;
    private final int httpResult;

    public UnsuccessfulDocumentDownload(final int httpResult,
                                        final String responseBody) {
        this.responseBody = responseBody;
        this.httpResult = httpResult;
    }

    @Override
    public boolean downloadSuccessful() {
        return false;
    }

    @Override
    public int getHttpResult() {
        return httpResult;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
