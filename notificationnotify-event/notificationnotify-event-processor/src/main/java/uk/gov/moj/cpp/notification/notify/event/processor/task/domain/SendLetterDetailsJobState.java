package uk.gov.moj.cpp.notification.notify.event.processor.task.domain;

import java.util.UUID;

public class SendLetterDetailsJobState extends NotificationJobState<SendLetterDetails> {

    public SendLetterDetailsJobState(
            final UUID notificationId,
            final SendLetterDetails sendLetterDetails) {

        super(notificationId, sendLetterDetails);
    }
}
