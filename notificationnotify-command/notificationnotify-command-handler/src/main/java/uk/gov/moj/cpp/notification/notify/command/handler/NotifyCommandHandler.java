package uk.gov.moj.cpp.notification.notify.command.handler;

import static java.util.UUID.randomUUID;
import static javax.json.JsonValue.NULL;
import static uk.gov.justice.services.core.annotation.Component.COMMAND_HANDLER;
import static uk.gov.justice.services.core.enveloper.Enveloper.toEnvelopeWithMetadataFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;

import uk.gov.justice.json.schemas.domains.notificationnotify.MarkAsAttempted;
import uk.gov.justice.json.schemas.domains.notificationnotify.MarkAsFailed;
import uk.gov.justice.json.schemas.domains.notificationnotify.MarkAsInvalid;
import uk.gov.justice.json.schemas.domains.notificationnotify.MarkAsSent;
import uk.gov.justice.json.schemas.domains.notificationnotify.ProcessBouncedEmail;
import uk.gov.justice.json.schemas.domains.notificationnotify.RecordCheckBouncedEmailRequestFailed;
import uk.gov.justice.json.schemas.domains.notificationnotify.RecordCheckPocaEmailRequestFailed;
import uk.gov.justice.json.schemas.domains.notificationnotify.SendEmailNotification;
import uk.gov.justice.json.schemas.domains.notificationnotify.SendLetterNotification;
import uk.gov.justice.services.core.aggregate.AggregateService;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.eventsourcing.source.core.EventSource;
import uk.gov.justice.services.eventsourcing.source.core.EventStream;
import uk.gov.justice.services.eventsourcing.source.core.exception.EventStreamException;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.notification.notify.domain.aggregate.Notification;
import uk.gov.moj.cpp.notification.notify.domain.aggregate.NotificationMonitor;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import javax.inject.Inject;

@ServiceComponent(COMMAND_HANDLER)
public class NotifyCommandHandler {

    @Inject
    private AggregateService aggregateService;

    @Inject
    private EventSource eventSource;

    @Inject
    private Enveloper enveloper;

    @Handles("notificationnotify.command.send-email-notification")
    @SuppressWarnings("squid:S2789")//this is avoid Sonar null check Major Warning
    public void sendEmail(final Envelope<SendEmailNotification> envelope) throws EventStreamException {

        final SendEmailNotification sendEmailNotification = envelope.payload();

        final UUID notificationId = sendEmailNotification.getNotificationId();
        final EventStream eventStream = eventSource.getStreamById(notificationId);
        final Notification aggregate = aggregateService.get(eventStream, Notification.class);
        final Stream<Object> events;

        if (sendEmailNotification.getMaterialUrl().isPresent()) {
            events = aggregate.sendWithMaterialAttachment(
                    notificationId, sendEmailNotification.getTemplateId(), sendEmailNotification.getSendToAddress(),
                    sendEmailNotification.getReplyToAddress(), sendEmailNotification.getReplyToAddressId(),
                    sendEmailNotification.getMaterialUrl(), sendEmailNotification.getPersonalisation(), sendEmailNotification.getClientContext());
        } else if (sendEmailNotification.getFileId().isPresent()) {
            events = aggregate.sendWithFileIdAttachment(
                    notificationId, sendEmailNotification.getTemplateId(), sendEmailNotification.getSendToAddress(),
                    sendEmailNotification.getReplyToAddress(), sendEmailNotification.getReplyToAddressId(),
                    sendEmailNotification.getFileId(), sendEmailNotification.getPersonalisation(), sendEmailNotification.getClientContext());
        } else {
            events = aggregate.send(
                    notificationId, sendEmailNotification.getTemplateId(),
                    sendEmailNotification.getSendToAddress(), sendEmailNotification.getReplyToAddress(),
                    sendEmailNotification.getReplyToAddressId(), sendEmailNotification.getPersonalisation(), sendEmailNotification.getClientContext());
        }
        appendEventsToStream(envelope, eventStream, events);

    }

    @Handles("notificationnotify.command.send-letter-notification")
    public void sendLetter(final Envelope<SendLetterNotification> envelope) throws EventStreamException {

        final SendLetterNotification sendLetterNotification = envelope.payload();

        final UUID notificationId = sendLetterNotification.getNotificationId();
        final Optional<String> clientContext = sendLetterNotification.getClientContext();
        final EventStream eventStream = eventSource.getStreamById(notificationId);
        final Notification aggregate = aggregateService.get(eventStream, Notification.class);

        final Stream<Object> events = aggregate.send(notificationId, sendLetterNotification.getLetterUrl(), sendLetterNotification.getPostage(), clientContext);

        appendEventsToStream(envelope, eventStream, events);
    }

    @Handles("notificationnotify.command.mark-as-invalid")
    public void markAsInvalid(final Envelope<MarkAsInvalid> envelope) throws EventStreamException {

        final MarkAsInvalid markAsInvalid = envelope.payload();

        final UUID notificationId = markAsInvalid.getNotificationId();

        final EventStream eventStream = eventSource.getStreamById(notificationId);
        final Notification notification = aggregateService.get(eventStream, Notification.class);

        final Stream<Object> events = notification.markAsInvalid(notificationId,
                markAsInvalid.getErrorMessage(),
                markAsInvalid.getFailedTime());

        appendEventsToStream(envelope, eventStream, events);
    }

    @Handles("notificationnotify.command.mark-as-sent")
    public void markAsSent(final Envelope<MarkAsSent> envelope) throws EventStreamException {

        final MarkAsSent markAsSent = envelope.payload();

        final UUID notificationId = markAsSent.getNotificationId();
        final EventStream eventStream = eventSource.getStreamById(notificationId);
        final Notification aggregate = aggregateService.get(eventStream, Notification.class);

        final Stream<Object> events = aggregate.markAsSent(markAsSent.getSentTime(),
                markAsSent.getSendToAddress(),
                markAsSent.getReplyToAddress(),
                markAsSent.getEmailSubject(),
                markAsSent.getEmailBody(),
                markAsSent.getCompletedAt()
        );

        appendEventsToStream(envelope, eventStream, events);
    }

    @Handles("notificationnotify.command.mark-as-failed")
    public void markAsFailed(final Envelope<MarkAsFailed> envelope) throws EventStreamException {

        final MarkAsFailed markAsFailed = envelope.payload();

        final EventStream eventStream = eventSource.getStreamById(markAsFailed.getNotificationId());
        final Notification aggregate = aggregateService.get(eventStream, Notification.class);

        final Stream<Object> events = aggregate.markAsFailed(markAsFailed.getFailedTime(), markAsFailed.getErrorMessage(), markAsFailed.getStatusCode());

        appendEventsToStream(envelope, eventStream, events);
    }

    @Handles("notificationnotify.command.mark-as-attempted")
    public void markAsAttempted(final Envelope<MarkAsAttempted> envelope) throws EventStreamException {

        final MarkAsAttempted markAsAttempted = envelope.payload();

        final EventStream eventStream = eventSource.getStreamById(markAsAttempted.getNotificationId());
        final Notification aggregate = aggregateService.get(eventStream, Notification.class);

        final Stream<Object> events = aggregate.markAsAttempted(markAsAttempted.getAttemptedTime(), markAsAttempted.getErrorMessage(), markAsAttempted.getStatusCode());
        appendEventsToStream(envelope, eventStream, events);
    }

    @Handles("notificationnotify.command.record-check-bounced-email-request-failed")
    public void recordBounceBackEmailRequestFailed(final Envelope<RecordCheckBouncedEmailRequestFailed> envelope) throws EventStreamException {
        final EventStream eventStream = eventSource.getStreamById(randomUUID());
        final NotificationMonitor aggregate = aggregateService.get(eventStream, NotificationMonitor.class);
        final RecordCheckBouncedEmailRequestFailed recordCheckBouncedEmailRequestFailed = envelope.payload();
        final Stream<Object> events = aggregate.recordCheckBouncedEmailRequestFailed(recordCheckBouncedEmailRequestFailed.getServer(), recordCheckBouncedEmailRequestFailed.getReason());
        appendEventsToStream(envelope, eventStream, events);
    }

    @Handles("notificationnotify.command.record-check-poca-email-request-failed")
    public void recordPocaEmailRequestFailed(final Envelope<RecordCheckPocaEmailRequestFailed> envelope) throws EventStreamException {
        final EventStream eventStream = eventSource.getStreamById(randomUUID());
        final NotificationMonitor aggregate = aggregateService.get(eventStream, NotificationMonitor.class);
        final RecordCheckPocaEmailRequestFailed recordCheckPocaEmailRequestFailed = envelope.payload();
        final Stream<Object> events = aggregate.recordCheckPocaEmailRequestFailed(recordCheckPocaEmailRequestFailed.getServer(), recordCheckPocaEmailRequestFailed.getReason());
        appendEventsToStream(envelope, eventStream, events);
    }

    @Handles("notificationnotify.command.process-bounced-email")
    public void processBouncedEmail(final Envelope<ProcessBouncedEmail> envelope) throws EventStreamException {

        final ProcessBouncedEmail processBouncedEmail = envelope.payload();

        final UUID notificationId = processBouncedEmail.getNotificationId();
        final EventStream eventStream = eventSource.getStreamById(notificationId);
        final Notification aggregate = aggregateService.get(eventStream, Notification.class);
        final Stream<Object> events = aggregate.processBouncedEmail();

        appendEventsToStream(envelope, eventStream, events);
    }

    private void appendEventsToStream(final Envelope<?> envelope, final EventStream eventStream, final Stream<Object> events) throws EventStreamException {
        final JsonEnvelope jsonEnvelope = envelopeFrom(envelope.metadata(), NULL);

        eventStream.append(events.map(toEnvelopeWithMetadataFrom(jsonEnvelope)));

    }

}
