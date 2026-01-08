package uk.gov.moj.cpp.notification.notify.command.handler;

import static javax.json.JsonValue.NULL;
import static uk.gov.justice.services.core.annotation.Component.COMMAND_HANDLER;
import static uk.gov.justice.services.core.enveloper.Enveloper.toEnvelopeWithMetadataFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;

import uk.gov.justice.json.schemas.domains.notificationnotify.ProcessPocaEmail;
import uk.gov.justice.services.core.aggregate.AggregateService;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.eventsourcing.source.core.EventSource;
import uk.gov.justice.services.eventsourcing.source.core.EventStream;
import uk.gov.justice.services.eventsourcing.source.core.exception.EventStreamException;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.notification.notify.domain.aggregate.PocaAggregate;

import java.util.UUID;
import java.util.stream.Stream;

import javax.inject.Inject;

@ServiceComponent(COMMAND_HANDLER)
public class PocaApplicationCommandHandler {

    @Inject
    private AggregateService aggregateService;

    @Inject
    private EventSource eventSource;

    @Inject
    private Enveloper enveloper;

    @Handles("notificationnotify.command.process-poca-email")
    public void processPocaEmail(final Envelope<ProcessPocaEmail> envelope) throws EventStreamException {

        final ProcessPocaEmail processPocaEmail = envelope.payload();
        final UUID pocaMailId = processPocaEmail.getPocaMailId();
        final EventStream eventStream = eventSource.getStreamById(pocaMailId);
        final PocaAggregate aggregate = aggregateService.get(eventStream, PocaAggregate.class);
        final Stream<Object> events = aggregate.processPocaEmail(processPocaEmail.getPocaFileId(), processPocaEmail.getPocaMailId(), processPocaEmail.getSenderEmail(), processPocaEmail.getSubject());

        appendEventsToStream(envelope, eventStream, events);
    }

    private void appendEventsToStream(final Envelope<?> envelope, final EventStream eventStream, final Stream<Object> events) throws EventStreamException {
        final JsonEnvelope jsonEnvelope = envelopeFrom(envelope.metadata(), NULL);
        eventStream.append(events.map(toEnvelopeWithMetadataFrom(jsonEnvelope)));
    }
}