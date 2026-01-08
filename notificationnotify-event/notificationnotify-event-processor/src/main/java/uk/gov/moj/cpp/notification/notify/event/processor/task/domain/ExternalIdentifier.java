package uk.gov.moj.cpp.notification.notify.event.processor.task.domain;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;

public class ExternalIdentifier {

    private final UUID externalNotificationId;
    private ExtractedSendEmailResponse extractedSendEmailResponse;

    @JsonCreator
    public ExternalIdentifier(final UUID externalNotificationId,
                              final ExtractedSendEmailResponse extractedSendEmailResponse) {
        this.externalNotificationId = externalNotificationId;
        this.extractedSendEmailResponse = extractedSendEmailResponse;
    }

    public UUID getExternalNotificationId() {
        return externalNotificationId;
    }

    public Object getExtractedSendEmailResponse() {
        return extractedSendEmailResponse;
    }
}
