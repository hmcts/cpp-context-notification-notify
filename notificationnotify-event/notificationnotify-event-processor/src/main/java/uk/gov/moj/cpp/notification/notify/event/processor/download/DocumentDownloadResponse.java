package uk.gov.moj.cpp.notification.notify.event.processor.download;

public interface DocumentDownloadResponse {

    boolean downloadSuccessful();

    int getHttpResult();
}
