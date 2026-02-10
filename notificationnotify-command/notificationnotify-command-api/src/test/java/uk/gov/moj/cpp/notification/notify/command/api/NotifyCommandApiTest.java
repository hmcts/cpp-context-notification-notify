package uk.gov.moj.cpp.notification.notify.command.api;

import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.verify;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;
import static uk.gov.justice.services.test.utils.core.enveloper.EnveloperFactory.createEnveloper;

import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.spi.DefaultEnvelope;

import java.util.UUID;

import javax.json.JsonObjectBuilder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class NotifyCommandApiTest {

    @Mock
    Sender sender;
    @InjectMocks
    NotifyCommandApi notifyCommandApi;
    @Spy
    private final Enveloper enveloper = createEnveloper();
    @Captor
    private ArgumentCaptor<DefaultEnvelope> jsonEnvelopeArgumentCaptor;

    @Test
    public void shouldSendEmail() {
        final String commandName = "notificationnotify.send-email-notification";
        final JsonObjectBuilder payload = createObjectBuilder().add("id", "value");
        final JsonEnvelope jsonEnvelope = envelopeFrom(metadataBuilder()
                .withId(UUID.randomUUID())
                .withName(commandName), payload);

        notifyCommandApi.sendEmail(jsonEnvelope);

        verify(sender).send(jsonEnvelopeArgumentCaptor.capture());
        assertThat(jsonEnvelopeArgumentCaptor.getValue().metadata().name(), is("notificationnotify.command.send-email-notification"));
        assertThat(jsonEnvelopeArgumentCaptor.getValue().payload(), is(createObjectBuilder().add("id", "value").build()));
    }

    @Test
    public void shouldSendEmailWithMaterialUrl() {
        final String commandName = "notificationnotify.send-email-notification";
        final String materialLink = "http://localhost:8080/material-query-api/query/api/rest/material/b439f425-e894-4a2c-aeb8-ed172565720f";
        final JsonObjectBuilder payload = createObjectBuilder()
                .add("id", "value")
                .add("materialUrl", materialLink);
        final JsonEnvelope jsonEnvelope = envelopeFrom(metadataBuilder()
                .withId(UUID.randomUUID())
                .withName(commandName), payload);

        notifyCommandApi.sendEmail(jsonEnvelope);

        verify(sender).send(jsonEnvelopeArgumentCaptor.capture());
        assertThat(jsonEnvelopeArgumentCaptor.getValue().metadata().name(), is("notificationnotify.command.send-email-notification"));
        assertThat(jsonEnvelopeArgumentCaptor.getValue().payload(),
                is(createObjectBuilder().add("id", "value")
                        .add("materialUrl", materialLink)
                        .build()));
    }

    @Test
    public void shouldSendEmailWithFileId() {
        final String commandName = "notificationnotify.send-email-notification";
        final String fileId = UUID.randomUUID().toString();
        final JsonObjectBuilder payload = createObjectBuilder()
                .add("id", "value")
                .add("fileId", fileId);
        final JsonEnvelope jsonEnvelope = envelopeFrom(metadataBuilder()
                .withId(UUID.randomUUID())
                .withName(commandName), payload);

        notifyCommandApi.sendEmail(jsonEnvelope);

        verify(sender).send(jsonEnvelopeArgumentCaptor.capture());
        assertThat(jsonEnvelopeArgumentCaptor.getValue().metadata().name(), is("notificationnotify.command.send-email-notification"));
        assertThat(jsonEnvelopeArgumentCaptor.getValue().payload(),
                is(createObjectBuilder().add("id", "value")
                        .add("fileId", fileId)
                        .build()));
    }

    @Test
    public void shouldSendLetter() {
        final String commandName = "notificationnotify.send-letter-notification";
        final JsonObjectBuilder payload = createObjectBuilder().add("id", "value");
        final JsonEnvelope jsonEnvelope = envelopeFrom(metadataBuilder()
                .withId(UUID.randomUUID())
                .withName(commandName), payload);

        notifyCommandApi.sendLetter(jsonEnvelope);

        verify(sender).send(jsonEnvelopeArgumentCaptor.capture());
        assertThat(jsonEnvelopeArgumentCaptor.getValue().metadata().name(), is("notificationnotify.command.send-letter-notification"));
        assertThat(jsonEnvelopeArgumentCaptor.getValue().payload(),
                is(createObjectBuilder().add("id", "value")
                        .build()));
    }

}
