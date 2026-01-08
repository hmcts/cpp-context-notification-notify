package uk.gov.moj.cpp.notification.notify.command.api;

import static uk.gov.justice.services.core.annotation.Component.COMMAND_API;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;

import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;

import javax.inject.Inject;

@ServiceComponent(COMMAND_API)
public class NotifyCommandApi {

    @Inject
    private Sender sender;

    @Inject
    private Enveloper enveloper;

    @Handles("notificationnotify.send-email-notification")
    public void sendEmail(final JsonEnvelope envelope) {
        final Metadata metadata = metadataFrom(envelope.metadata())
                .withName("notificationnotify.command.send-email-notification")
                .build();

        sender.send(envelopeFrom(metadata, envelope.payload()));
    }

    @Handles("notificationnotify.send-letter-notification")
    public void sendLetter(final JsonEnvelope envelope) {

        final Metadata metadata = metadataFrom(envelope.metadata())
                .withName("notificationnotify.command.send-letter-notification")
                .build();

        sender.send(envelopeFrom(metadata, envelope.payload()));
    }
}
