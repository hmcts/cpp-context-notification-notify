package uk.gov.moj.cpp.notification.notify.domain.aggregate;

import static java.lang.String.format;
import static java.util.stream.Stream.empty;
import static java.util.stream.Stream.of;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static uk.gov.justice.domain.aggregate.matcher.EventSwitcher.doNothing;
import static uk.gov.justice.domain.aggregate.matcher.EventSwitcher.match;
import static uk.gov.justice.domain.aggregate.matcher.EventSwitcher.otherwiseDoNothing;
import static uk.gov.justice.domain.aggregate.matcher.EventSwitcher.when;
import static uk.gov.justice.json.schemas.domains.notificationnotify.BouncedEmailAlreadyNotified.bouncedEmailAlreadyNotified;
import static uk.gov.justice.json.schemas.domains.notificationnotify.EmailNotificationBounced.emailNotificationBounced;
import static uk.gov.justice.json.schemas.domains.notificationnotify.FirstClassLetterQueued.firstClassLetterQueued;
import static uk.gov.justice.json.schemas.domains.notificationnotify.LetterQueued.letterQueued;
import static uk.gov.justice.json.schemas.domains.notificationnotify.LetterQueuedForResend.letterQueuedForResend;
import static uk.gov.justice.json.schemas.domains.notificationnotify.NotificationAttempted.notificationAttempted;
import static uk.gov.justice.json.schemas.domains.notificationnotify.NotificationFailed.notificationFailed;
import static uk.gov.justice.json.schemas.domains.notificationnotify.NotificationQueued.notificationQueued;
import static uk.gov.justice.json.schemas.domains.notificationnotify.NotificationSent.notificationSent;

import uk.gov.justice.domain.aggregate.Aggregate;
import uk.gov.justice.json.schemas.domains.notificationnotify.EmailNotificationBounced;
import uk.gov.justice.json.schemas.domains.notificationnotify.FirstClassLetterQueued;
import uk.gov.justice.json.schemas.domains.notificationnotify.LetterQueued;
import uk.gov.justice.json.schemas.domains.notificationnotify.LetterQueuedForResend;
import uk.gov.justice.json.schemas.domains.notificationnotify.NotificationAttempted;
import uk.gov.justice.json.schemas.domains.notificationnotify.NotificationFailed;
import uk.gov.justice.json.schemas.domains.notificationnotify.NotificationQueued;
import uk.gov.justice.json.schemas.domains.notificationnotify.NotificationSent;
import uk.gov.justice.json.schemas.domains.notificationnotify.Personalisation;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public class Notification implements Aggregate {

    private static final String FIRST_CLASS_POSTAGE = "first";

    private static final long serialVersionUID = 2L;

    private UUID notificationId;
    private int resendAttemptsRemaining = 5;
    private String letterUrl;
    private String postage;
    private String clientContext;
    private boolean isBouncedEmailNotified;

    public Stream<Object> send(final UUID notificationId,
                               final UUID templateId,
                               final String sendToAddress,
                               final Optional<String> replyToAddress,
                               final Optional<UUID> replyToAddressId,
                               final Optional<Personalisation> personalisation,
                               final Optional<String> clientContext) {

        return sendEmail(notificationId, templateId, sendToAddress, replyToAddress, replyToAddressId, personalisation, Optional.empty(), Optional.empty(), clientContext);
    }

    public Stream<Object> sendWithMaterialAttachment(final UUID notificationId,
                                                     final UUID templateId,
                                                     final String sendToAddress,
                                                     final Optional<String> replyToAddress,
                                                     final Optional<UUID> replyToAddressId,
                                                     final Optional<String> materialUrl,
                                                     final Optional<Personalisation> personalisation,
                                                     final Optional<String> clientContext) {

        return sendEmail(notificationId, templateId, sendToAddress, replyToAddress, replyToAddressId, personalisation, materialUrl, Optional.empty(), clientContext);
    }

    public Stream<Object> sendWithFileIdAttachment(final UUID notificationId,
                                                   final UUID templateId,
                                                   final String sendToAddress,
                                                   final Optional<String> replyToAddress,
                                                   final Optional<UUID> replyToAddressId,
                                                   final Optional<UUID> fileId,
                                                   final Optional<Personalisation> personalisation,
                                                   final Optional<String> clientContext) {

        return sendEmail(notificationId, templateId, sendToAddress, replyToAddress, replyToAddressId, personalisation, Optional.empty(), fileId, clientContext);
    }

    private Stream<Object> sendEmail(final UUID notificationId,
                                     final UUID templateId,
                                     final String sendToAddress,
                                     final Optional<String> replyToAddress,
                                     final Optional<UUID> replyToAddressId,
                                     final Optional<Personalisation> personalisation,
                                     final Optional<String> materialUrl,
                                     final Optional<UUID> fileId,
                                     final Optional<String> clientContext) {
        if (isDuplicateNotification()) {
            return empty();
        }

        final NotificationQueued notificationQueued = notificationQueued()
                .withNotificationId(notificationId)
                .withTemplateId(templateId)
                .withSendToAddress(sendToAddress)
                .withReplyToAddress(replyToAddress.orElse(null))
                .withReplyToAddressId(replyToAddressId.orElse(null))
                .withPersonalisation(personalisation.orElse(null))
                .withMaterialUrl(materialUrl.orElse(null))
                .withFileId(fileId.orElse(null))
                .withClientContext(clientContext.orElse(null))
                .build();

        return apply(of(notificationQueued));
    }

    public Stream<Object> send(final UUID notificationId, final String url, final Optional<String> postage, final Optional<String> clientContext) {

        if (isDuplicateNotification()) {
            return empty();
        }

        if (FIRST_CLASS_POSTAGE.equalsIgnoreCase(postage.orElse(null))) {
            return apply(of(firstClassLetterQueued()
                    .withLetterUrl(url)
                    .withNotificationId(notificationId)
                    .withClientContext(clientContext.orElse(null))
                    .build()
            ));
        } else {
            return apply(of(letterQueued()
                    .withLetterUrl(url)
                    .withNotificationId(notificationId)
                    .withClientContext(clientContext)
                    .build()
            ));
        }
    }

    public Stream<Object> markAsInvalid(final UUID notificationId,
                                        final String errorMessage,
                                        final ZonedDateTime failedTime) {
        final String message = format("%s automatic resend attempt, remaining = %s", errorMessage, resendAttemptsRemaining);

        if (resendAttemptsRemaining > 0) {
            return apply(of(letterQueuedForResend()
                    .withLetterUrl(letterUrl)
                    .withNotificationId(notificationId)
                    .withReason(message)
                    .withPostage(postage)
                    .build()

            ));
        }
        return apply(of(notificationFailed()
                .withNotificationId(notificationId)
                .withFailedTime(failedTime)
                .withErrorMessage(message)
                .withStatusCode(BAD_REQUEST.getStatusCode())
                .build()
        ));
    }

    public Stream<Object> markAsSent(final ZonedDateTime sentTime,
                                     final Optional<String> sendToAddress,
                                     final Optional<String> replyToAddress,
                                     final Optional<String> emailSubject,
                                     final Optional<String> emailBody,
                                     final Optional<ZonedDateTime> completedAt
    ) {
        return apply(Stream.of(notificationSent()
                .withNotificationId(notificationId)
                .withClientContext(this.clientContext)
                .withCompletedAt(completedAt)
                .withSendToAddress(sendToAddress)
                .withReplyToAddress(replyToAddress)
                .withEmailSubject(emailSubject)
                .withEmailBody(emailBody)
                .withSentTime(sentTime)
                .build()));
    }

    public Stream<Object> processBouncedEmail() {
        if (!isBouncedEmailNotified) {
            return apply(of(emailNotificationBounced()
                    .withNotificationId(notificationId)
                    .withClientContext(clientContext)
                    .build()

            ));
        } else {
            return apply(of(bouncedEmailAlreadyNotified()
                    .withNotificationId(notificationId)
                    .withClientContext(clientContext)
                    .build()

            ));
        }
    }

    public Stream<Object> markAsAttempted(final ZonedDateTime attemptedTime,
                                          final String errorMessage,
                                          final Integer statusCode) {
        return apply(of(notificationAttempted()
                .withNotificationId(notificationId)
                .withAttemptedTime(attemptedTime)
                .withErrorMessage(errorMessage)
                .withStatusCode(statusCode)
                .build()
        ));
    }

    public Stream<Object> markAsFailed(final ZonedDateTime failedTime,
                                       final String errorMessage,
                                       final Optional<Integer> statusCode) {

        return apply(of(notificationFailed()
                .withNotificationId(notificationId)
                .withFailedTime(failedTime)
                .withErrorMessage(errorMessage)
                .withStatusCode(statusCode.orElse(null))
                .withClientContext(this.clientContext)
                .build()
        ));
    }

    @Override
    public Object apply(Object event) {
        return match(event).with(
                when(NotificationQueued.class).apply(this::recordEmailQueued),
                when(LetterQueued.class).apply(l -> recordLetterQueued(l.getNotificationId(), l.getLetterUrl(), "", l.getClientContext().orElse(null))),
                when(FirstClassLetterQueued.class).apply(l -> recordLetterQueued(l.getNotificationId(), l.getLetterUrl(), FIRST_CLASS_POSTAGE, l.getClientContext().orElse(null))),
                when(LetterQueuedForResend.class).apply(x -> resendAttemptsRemaining--),
                when(NotificationAttempted.class).apply(x -> doNothing()),
                when(NotificationFailed.class).apply(x -> doNothing()),
                when(NotificationSent.class).apply(x -> doNothing()),
                when(EmailNotificationBounced.class).apply(emailNotificationBounced -> this.isBouncedEmailNotified = true),
                otherwiseDoNothing()
        );
    }

    private boolean isDuplicateNotification() {
        return this.notificationId != null;
    }

    private void recordEmailQueued(final NotificationQueued notificationQueued) {
        this.notificationId = notificationQueued.getNotificationId();
        this.clientContext = notificationQueued.getClientContext().orElse(null);
    }

    private void recordLetterQueued(final UUID notificationId, final String letterUrl, final String postage, final String clientContext) {
        this.notificationId = notificationId;
        this.letterUrl = letterUrl;
        this.postage = postage;
        this.clientContext = clientContext;
    }
}
