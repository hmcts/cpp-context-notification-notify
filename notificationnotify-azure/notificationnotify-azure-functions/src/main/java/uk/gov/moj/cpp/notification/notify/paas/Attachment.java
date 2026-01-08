package uk.gov.moj.cpp.notification.notify.paas;

public class Attachment {
    private String content;
    private String filename;

    public String getContent() {
        return content;
    }

    public void setContent(final String content) {
        this.content = content;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(final String filename) {
        this.filename = filename;
    }
}