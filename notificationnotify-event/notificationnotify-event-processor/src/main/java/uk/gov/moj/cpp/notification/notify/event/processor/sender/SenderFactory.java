package uk.gov.moj.cpp.notification.notify.event.processor.sender;

import uk.gov.moj.cpp.notification.notify.event.processor.task.Task;

import jakarta.inject.Inject;

public class SenderFactory {

    @Inject
    private EmailSender emailSender;

    @Inject
    private LetterSender letterSender;

    public NotificationSender senderFor(final Task task) {

        if (task.isEmailTask()) {
            return emailSender;
        }

        return letterSender;
    }
}
