package uk.gov.moj.cpp.notification.notify.event.listener;

import static uk.gov.justice.services.core.annotation.Component.EVENT_LISTENER;
import static uk.gov.moj.cpp.notification.notify.event.listener.NotificationStatus.ATTEMPTED;
import static uk.gov.moj.cpp.notification.notify.event.listener.NotificationStatus.FAILED;
import static uk.gov.moj.cpp.notification.notify.event.listener.NotificationStatus.QUEUED;
import static uk.gov.moj.cpp.notification.notify.event.listener.NotificationStatus.REQUEUED;
import static uk.gov.moj.cpp.notification.notify.event.listener.NotificationStatus.SENT;
import static uk.gov.moj.cpp.notification.notify.event.listener.NotificationStatus.URL_FAILED;
import static uk.gov.moj.cpp.notification.notify.event.listener.NotificationStatus.VALIDATION_FAILED;

import uk.gov.justice.json.schemas.domains.notificationnotify.FirstClassLetterQueued;
import uk.gov.justice.json.schemas.domains.notificationnotify.LetterQueued;
import uk.gov.justice.json.schemas.domains.notificationnotify.LetterQueuedForResend;
import uk.gov.justice.json.schemas.domains.notificationnotify.NotificationAttempted;
import uk.gov.justice.json.schemas.domains.notificationnotify.NotificationFailed;
import uk.gov.justice.json.schemas.domains.notificationnotify.NotificationQueued;
import uk.gov.justice.json.schemas.domains.notificationnotify.NotificationSent;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.notification.entity.Notification;
import uk.gov.moj.cpp.notification.factory.NotificationFactory;
import uk.gov.moj.cpp.notification.repository.NotificationRepository;

import java.time.ZonedDateTime;
import java.util.UUID;

import javax.inject.Inject;

@ServiceComponent(EVENT_LISTENER)
public class NotifyEventListener {

    @Inject
    private UtcClock clock;

    @Inject
    private NotificationRepository notificationRepository;

    @Inject
    private NotificationFactory notificationFactory;

    @Handles("notificationnotify.events.notification-queued")
    public void notificationQueued(final Envelope<NotificationQueued> event) {
        final NotificationQueued notificationQueued = event.payload();
        final ZonedDateTime timestamp = clock.now();

        final Notification notification = notificationFactory.createEmailNotification(
                notificationQueued.getNotificationId(),
                QUEUED.name(),
                notificationQueued.getMaterialUrl().orElse(""),
                notificationQueued.getSendToAddress(),
                timestamp,
                timestamp);

        notificationRepository.save(notification);
    }

    @Handles("notificationnotify.events.letter-queued")
    public void letterQueued(final Envelope<LetterQueued> event) {
        final LetterQueued letterQueued = event.payload();

        saveLetterQueuedNotification(letterQueued.getNotificationId(), letterQueued.getLetterUrl());
    }

    @Handles("notificationnotify.events.first-class-letter-queued")
    public void firstClassLetterQueued(final Envelope<FirstClassLetterQueued> event) {
        final FirstClassLetterQueued firstClassLetterQueued = event.payload();

        saveLetterQueuedNotification(firstClassLetterQueued.getNotificationId(), firstClassLetterQueued.getLetterUrl());
    }

    @Handles("notificationnotify.events.letter-queued-for-resend")
    public void letterQueuedForResend(final Envelope<LetterQueuedForResend> event) {
        final LetterQueuedForResend letterQueuedForResend = event.payload();
        final ZonedDateTime dateCreated = clock.now();
        final ZonedDateTime lastUpdated = clock.now();

        final Notification notification = notificationFactory.createLetterNotification(
                letterQueuedForResend.getNotificationId(),
                REQUEUED.name(),
                letterQueuedForResend.getLetterUrl(),
                dateCreated,
                lastUpdated);

        notificationRepository.save(notification);
    }

    @Handles("notificationnotify.events.notification-attempted")
    public void notificationAttempted(final Envelope<NotificationAttempted> event) {
        final NotificationAttempted notificationAttempted = event.payload();

        final Notification notification = notificationRepository.findBy(notificationAttempted.getNotificationId());

        if (isNotificationAllowedToAttempt(notification.getStatus())) {
            notification.setErrorMessage(notificationAttempted.getErrorMessage());
            notification.setStatusCode(notificationAttempted.getStatusCode());
            notification.setLastUpdated(notificationAttempted.getAttemptedTime());
            notification.setStatus(ATTEMPTED.name());

            notificationRepository.save(notification);
        }
    }

    @Handles("notificationnotify.events.notification-failed")
    public void notificationFailed(final Envelope<NotificationFailed> event) {
        final NotificationFailed notificationFailed = event.payload();

        final Notification notification = notificationRepository.findBy(notificationFailed.getNotificationId());

        notification.setErrorMessage(notificationFailed.getErrorMessage());
        notification.setLastUpdated(notificationFailed.getFailedTime());
        notification.setStatus(FAILED.name());

        notificationFailed.getStatusCode().ifPresent(notification::setStatusCode);

        notificationRepository.save(notification);
    }

    @Handles("notificationnotify.events.notification-sent")
    public void notificationSent(final Envelope<NotificationSent> event) {
        final NotificationSent notificationSent = event.payload();

        final Notification notification = notificationRepository.findBy(notificationSent.getNotificationId());

        notification.setLastUpdated(notificationSent.getSentTime());
        notification.setStatus(SENT.name());

        notificationRepository.save(notification);
    }

    private boolean isNotificationAllowedToAttempt(final String notificationStatus) {
        return !(FAILED.name().equals(notificationStatus) ||
                SENT.name().equals(notificationStatus) ||
                VALIDATION_FAILED.name().equals(notificationStatus) ||
                URL_FAILED.name().equals(notificationStatus));
    }

    private void saveLetterQueuedNotification(final UUID notificationId, final String letterUrl) {
        final ZonedDateTime timestamp = clock.now();

        final Notification notification = notificationFactory.createLetterNotification(
                notificationId,
                QUEUED.name(),
                letterUrl,
                timestamp,
                timestamp);

        notificationRepository.save(notification);
    }
}
