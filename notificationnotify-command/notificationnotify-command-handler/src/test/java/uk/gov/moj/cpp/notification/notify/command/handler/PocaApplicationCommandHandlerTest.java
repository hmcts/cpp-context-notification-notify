package uk.gov.moj.cpp.notification.notify.command.handler;

import static java.util.UUID.randomUUID;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.json.schemas.domains.notificationnotify.ProcessPocaEmail.processPocaEmail;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;

import uk.gov.justice.json.schemas.domains.notificationnotify.ProcessPocaEmail;
import uk.gov.justice.services.core.aggregate.AggregateService;
import uk.gov.justice.services.eventsourcing.source.core.EventSource;
import uk.gov.justice.services.eventsourcing.source.core.EventStream;
import uk.gov.justice.services.eventsourcing.source.core.exception.EventStreamException;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.notification.notify.domain.aggregate.PocaAggregate;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PocaApplicationCommandHandlerTest {

    @Mock
    PocaAggregate aggregate;

    @Mock
    AggregateService aggregateService;

    @Mock
    EventSource eventSource;

    @Mock
    EventStream eventStream;

    @Mock
    Stream<Object> events;

    @InjectMocks
    PocaApplicationCommandHandler pocaApplicationCommandHandler;

    @Test
    public void shouldProcessPocaEmail() throws EventStreamException {

        final UUID pocaFileId = randomUUID();
        final UUID pocaMailId = randomUUID();
        final String senderEmail = "test@test.com";
        final String emailSubject = "test subject";

        final ProcessPocaEmail processPocaEmail = processPocaEmail()
                .withPocaFileId(pocaFileId)
                .withPocaMailId(pocaMailId)
                .withSenderEmail(senderEmail)
                .withSubject(emailSubject)
                .build();

        when(aggregateService.get(eventStream, PocaAggregate.class)).thenReturn(aggregate);
        when(eventSource.getStreamById(pocaMailId)).thenReturn(eventStream);
        when(aggregate.processPocaEmail(processPocaEmail.getPocaFileId(), processPocaEmail.getPocaMailId(), processPocaEmail.getSenderEmail(), processPocaEmail.getSubject())).thenReturn(events);

        final Envelope<ProcessPocaEmail> processPocaEmailEnvelope = envelopeFrom(metadataBuilder().withId(randomUUID())
                .withName("notificationnotify.command.process-poca-email"), processPocaEmail);

        pocaApplicationCommandHandler.processPocaEmail(processPocaEmailEnvelope);

        verify(aggregateService).get(eventStream, PocaAggregate.class);
        verify(aggregate, times(1)).processPocaEmail(pocaFileId,pocaMailId, senderEmail, emailSubject);
    }
}