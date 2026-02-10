package uk.gov.moj.cpp.notification.notify.event.processor;

import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;

import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.core.annotation.FrameworkComponent;
import uk.gov.justice.services.core.sender.Sender;

import java.util.UUID;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.json.JsonObject;

import org.slf4j.Logger;

@Stateless
public class PocaApplicationCommandSender {

    private static final Logger LOGGER = getLogger(PocaApplicationCommandSender.class);

    private static final String NOTIFICATION_NOTIFY_COMMAND_PROCESS_POCA_EMAIL = "notificationnotify.command.process-poca-email";

    @Inject
    @FrameworkComponent("EVENT_PROCESSOR")
    private Sender sender;

    @Inject
    private UtcClock utcClock;

    public void processPocaEmail(final UUID pocaFileId, final UUID pocaMailId, final String senderEmail, final String subject) {

        final JsonObject payload = createObjectBuilder()
                .add("pocaFileId", pocaFileId.toString())
                .add("pocaMailId", pocaMailId.toString())
                .add("senderEmail", senderEmail)
                .add("subject", subject)
                .build();
        sendCommandWith(pocaMailId, payload);
        LOGGER.info("PocaApplicationCommandSender sent command successfully");
    }

    private void sendCommandWith(final UUID pocaMailId, final JsonObject payload) {

        sender.send(envelopeFrom(
                metadataBuilder()
                        .withStreamId(pocaMailId)
                        .createdAt(utcClock.now())
                        .withName(NOTIFICATION_NOTIFY_COMMAND_PROCESS_POCA_EMAIL)
                        .withId(randomUUID())
                        .build(),
                payload));
    }
}
