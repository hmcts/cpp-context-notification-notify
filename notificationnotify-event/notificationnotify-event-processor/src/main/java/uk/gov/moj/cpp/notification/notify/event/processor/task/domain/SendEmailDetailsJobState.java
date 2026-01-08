package uk.gov.moj.cpp.notification.notify.event.processor.task.domain;

import java.util.UUID;

public class SendEmailDetailsJobState extends NotificationJobState<SendEmailDetails> {

    public SendEmailDetailsJobState(
            final UUID notificationId,
            final SendEmailDetails taskPayload) {
        super(notificationId, taskPayload);
    }
}
