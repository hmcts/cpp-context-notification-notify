package uk.gov.moj.cpp.notification.notify.event.processor;

import static java.util.Optional.ofNullable;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;

import uk.gov.justice.services.common.converter.ZonedDateTimes;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.core.annotation.FrameworkComponent;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.NotificationEmailDetails;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.CompleteHandler;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Stateless
public class NotificationNotifyCommandSender {
    private static final String ERROR_MESSAGE = "errorMessage";

    private static final String MARK_AS_SENT = "notificationnotify.command.mark-as-sent";
    private static final String MARK_AS_FAILED = "notificationnotify.command.mark-as-failed";
    private static final String MARK_AS_ATTEMPTED = "notificationnotify.command.mark-as-attempted";
    private static final String MARK_AS_INVALID = "notificationnotify.command.mark-as-invalid";
    private static final String NOTIFICATIONNOTIFY_COMMAND_PROCESS_BOUNCED_EMAIL = "notificationnotify.command.process-bounced-email";
    private static final String NOTIFICATIONNOTIFY_COMMAND_RECORD_CHECK_BOUNCED_EMAIL_REQUEST_FAILED = "notificationnotify.command.record-check-bounced-email-request-failed";
    private static final String NOTIFICATIONNOTIFY_COMMAND_RECORD_CHECK_POCA_EMAIL_REQUEST_FAILED = "notificationnotify.command.record-check-poca-email-request-failed";
    @Inject
    @FrameworkComponent("EVENT_PROCESSOR")
    private Sender sender;

    @Inject
    private UtcClock utcClock;

    @SuppressWarnings("squid:S1192")
    public void markAsSent(final UUID notificationId, NotificationEmailDetails notificationEmailDetails) {
        final JsonObjectBuilder builder = createObjectBuilder()
                .add("notificationId", notificationId.toString())
                .add("sentTime", ZonedDateTimes.toString(utcClock.now()));

        ofNullable(notificationEmailDetails.getReplyToAddress())
                .filter(replyToAddress -> !replyToAddress.isEmpty())
                .ifPresent(replyToAddress -> builder.add("replyToAddress", replyToAddress));

        ofNullable(notificationEmailDetails.getSendToAddress())
                .filter(s -> !s.isEmpty())
                .ifPresent(s -> builder.add("sendToAddress", s));

        ofNullable(notificationEmailDetails.getEmailSubject())
                .filter(s -> !s.isEmpty())
                .ifPresent(s -> builder.add("emailSubject", s));

        ofNullable(notificationEmailDetails.getEmailBody())
                .filter(s -> !s.isEmpty())
                .ifPresent(s -> builder.add("emailBody", s));

        ofNullable(notificationEmailDetails.getCompletedAt())
                .ifPresent(dt -> builder.add("completedAt", ZonedDateTimes.toString(dt)));

        final JsonObject payload = builder.build();

        sendCommandWith(MARK_AS_SENT, notificationId, payload);
    }

    public void processBouncedEmail(final UUID notificationId) {

        final JsonObject payload = createObjectBuilder()
                .add("notificationId", notificationId.toString())
                .build();

        sendCommandWith(NOTIFICATIONNOTIFY_COMMAND_PROCESS_BOUNCED_EMAIL, notificationId, payload);
    }

    public void recordCheckBouncedEmailRequestAsFailed(final String server, final String reason) {

        final JsonObject payload = createObjectBuilder()
                .add("server", server)
                .add("reason", reason)
                .build();

        sendCommandWith(NOTIFICATIONNOTIFY_COMMAND_RECORD_CHECK_BOUNCED_EMAIL_REQUEST_FAILED, randomUUID(), payload);
    }

    public void recordCheckPocaEmailRequestAsFailed(final String server, final String reason) {

        final JsonObject payload = createObjectBuilder()
                .add("server", server)
                .add("reason", reason)
                .build();

        sendCommandWith(NOTIFICATIONNOTIFY_COMMAND_RECORD_CHECK_POCA_EMAIL_REQUEST_FAILED, randomUUID(), payload);
    }

    public void markNotificationFailed(final UUID notificationId, final String error, final Optional<Integer> statusCode) {

        final JsonObjectBuilder objectBuilder = createObjectBuilder()
                .add("notificationId", notificationId.toString())
                .add("failedTime", ZonedDateTimes.toString(utcClock.now()))
                .add(ERROR_MESSAGE, error);

        statusCode.ifPresent(status -> objectBuilder.add("statusCode", status));

        sendCommandWith(MARK_AS_FAILED, notificationId, objectBuilder.build());
    }

    public void markAsAttempted(final UUID notificationId, final String error, final int statusCode) {

        final JsonObject payload = createObjectBuilder()
                .add("notificationId", notificationId.toString())
                .add("attemptedTime", ZonedDateTimes.toString(utcClock.now()))
                .add(ERROR_MESSAGE, error)
                .add("statusCode", statusCode)
                .build();

        sendCommandWith(MARK_AS_ATTEMPTED, notificationId, payload);
    }

    public void markAsInvalid(final UUID notificationId,
                              final String errorMessage,
                              final ZonedDateTime failedTime) {

        final JsonObject payload = createObjectBuilder()
                .add("notificationId", notificationId.toString())
                .add(ERROR_MESSAGE, errorMessage)
                .add("failedTime", failedTime.toString())
                .build();

        sendCommandWith(MARK_AS_INVALID, notificationId, payload);
    }

    private void sendCommandWith(final String commandName, final UUID notificationId, final JsonObject payload) {

        sender.send(envelopeFrom(
                metadataBuilder()
                        .withStreamId(notificationId)
                        .createdAt(utcClock.now())
                        .withName(commandName)
                        .withId(randomUUID())
                        .build(),
                payload));
    }
}
