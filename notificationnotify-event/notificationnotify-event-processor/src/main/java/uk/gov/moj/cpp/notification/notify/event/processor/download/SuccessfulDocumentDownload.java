package uk.gov.moj.cpp.notification.notify.event.processor.download;

import java.io.InputStream;

@SuppressWarnings("squid:S1118")
public class SuccessfulDocumentDownload implements DocumentDownloadResponse {

    private final InputStream content;
    private final int httpResult;
    private final int contentSize;
    private final byte[] bytes;
    private final String fileName;

    public SuccessfulDocumentDownload(final int httpResult,
                                      final InputStream content,
                                      final int contentSize,
                                      final byte[] bytes,
                                      final String fileName) {
        this.httpResult = httpResult;
        this.content = content;
        this.contentSize = contentSize;
        this.bytes = bytes;
        this.fileName = fileName;
    }

    @Override
    public boolean downloadSuccessful() {
        return true;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public String getFileName() {
        return fileName;
    }

    @Override
    public int getHttpResult() {
        return httpResult;
    }

    public int contentSize() {
        return contentSize;
    }

    public InputStream getContent() {
        return content;
    }
}
