package uk.gov.moj.cpp.notification.notify.event.processor.task.domain;

public class SendLetterDetails {

    private final String documentUrl;

    private final String postage;

    public SendLetterDetails(final String documentUrl, final String postage) {
        this.documentUrl = documentUrl;
        this.postage = postage;
    }

    public String getDocumentUrl() {
        return documentUrl;
    }

    public String getPostage() {
        return postage;
    }
}
