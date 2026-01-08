package uk.gov.moj.cpp.notification.notify.event.processor.response;

import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExtractedSendEmailResponse;
import java.util.UUID;

public class SenderResponse implements NotificationResponse {

    private final UUID externalNotificationId;

    private final ExtractedSendEmailResponse extractedSendEmailResponse;

    public SenderResponse(final UUID externalNotificationId,
                          final ExtractedSendEmailResponse extractedSendEmailResponse) {
        this.externalNotificationId = externalNotificationId;
        this.extractedSendEmailResponse = extractedSendEmailResponse;
    }

    @Override
    public boolean isSuccessful() {
        return true;
    }

    public UUID getExternalNotificationId() {
        return externalNotificationId;
    }
    public ExtractedSendEmailResponse getExtractedSendEmailResponse() {
        return extractedSendEmailResponse;
    }
}
