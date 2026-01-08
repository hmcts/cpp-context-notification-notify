package uk.gov.moj.cpp.notification.notify.event.processor.task.domain;

import java.util.UUID;

public class ExternalIdentifierJobState extends NotificationJobState<ExternalIdentifier> {

    public ExternalIdentifierJobState(
            final UUID notificationId,
            final ExternalIdentifier taskPayload) {

        super(notificationId, taskPayload);
    }
}
