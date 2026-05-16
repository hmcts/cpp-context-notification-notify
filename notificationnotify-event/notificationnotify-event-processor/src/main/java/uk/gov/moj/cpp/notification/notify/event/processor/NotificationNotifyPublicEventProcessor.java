package uk.gov.moj.cpp.notification.notify.event.processor;

import static java.util.Objects.nonNull;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataFrom;

import com.azure.storage.blob.BlobContainerClient;

import uk.gov.justice.json.schemas.domains.notificationnotify.EmailNotificationBounced;
import uk.gov.justice.json.schemas.domains.notificationnotify.NotificationSent;
import uk.gov.justice.json.schemas.domains.notificationnotify.PocaEmailAlreadyReceived;
import uk.gov.justice.json.schemas.domains.notificationnotify.PocaEmailNotificationReceived;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.core.annotation.FrameworkComponent;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.moj.cpp.notification.notify.event.processor.sender.BlobFileEmailSender;
import uk.gov.moj.cpp.notification.notify.filestore.azure.StoragePath;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.justice.services.messaging.MetadataBuilder;
import uk.gov.moj.cpp.systemusers.ServiceContextSystemUserProvider;

import java.util.UUID;

import javax.inject.Inject;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceComponent(EVENT_PROCESSOR)
@SuppressWarnings("squid:S1192")
public class NotificationNotifyPublicEventProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationNotifyPublicEventProcessor.class.getName());

    public static final String PUBLIC_NOTIFICATIONNOTIFY_EVENTS_NOTIFICATION_BOUNCED = "public.notificationnotify.events.email-notification-bounced";
    public static final String PUBLIC_NOTIFICATIONNOTIFY_POCA_EMAIL_RECEIVED_NOTIFICATION = "public.notificationnotify.events.poca-email-notification-received";
    private static final String NOTIFICATION_MARK_AS_SENT_PUBLIC_EVENT = "public.notificationnotify.events.notification-sent";
    private static final String NOTIFICATION_MARK_AS_FAILED_PUBLIC_EVENT = "public.notificationnotify.events.notification-failed";
    private static final String CLIENT_CONTEXT = "clientContext";

    @FrameworkComponent("EVENT_PROCESSOR")
    @Inject
    private Sender sender;

    @Inject
    private Enveloper enveloper;

    @Inject
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;

    private static final StoragePath BLOB_PATH = StoragePath.internal();

    @Inject
    private BlobContainerClient blobContainerClient;

    @Inject
    private ServiceContextSystemUserProvider serviceContextSystemUserProvider;

    @Inject
    private BlobFileEmailSender blobFileEmailSender;


    @Handles("notificationnotify.events.notification-sent")
    public void notificationSuccess(final Envelope<NotificationSent> notificationSentEvent) {

        final MetadataBuilder builder = metadataFrom(notificationSentEvent.metadata());
        final UUID systemUser = serviceContextSystemUserProvider.getContextSystemUserId().orElse(null);

        final Metadata metadata = builder
                .withName(NOTIFICATION_MARK_AS_SENT_PUBLIC_EVENT)
                .withUserId(nonNull(systemUser) ? systemUser.toString() : null)
                .build();
        sender.send(envelopeFrom(metadata, objectToJsonObjectConverter.convert(notificationSentEvent.payload())));
    }

    @Handles("notificationnotify.events.email-notification-bounced")
    public void notificationBounced(final Envelope<EmailNotificationBounced> notificationBouncedEnvelope) {
        final MetadataBuilder builder = metadataFrom(notificationBouncedEnvelope.metadata());
        final Metadata metadata = builder
                .withName(PUBLIC_NOTIFICATIONNOTIFY_EVENTS_NOTIFICATION_BOUNCED)
                .build();
        sender.send(envelopeFrom(metadata, notificationBouncedEnvelope.payload()));
    }

    @Handles("notificationnotify.events.notification-failed")
    public void notificationFailed(final JsonEnvelope notificationFailedEvent) {
        final JsonObject payload = notificationFailedEvent.payloadAsJsonObject();
        final String notificationId = payload.getString("notificationId");
        final String failedTime = payload.getString("failedTime");
        final String errorMessage = payload.getString("errorMessage");


        final JsonObjectBuilder jsonObjectBuilder = createObjectBuilder()
                .add("notificationId", notificationId)
                .add("failedTime", failedTime)
                .add("errorMessage", errorMessage);

        if (payload.containsKey("statusCode")) {
            final int statusCode = payload.getInt("statusCode");
            jsonObjectBuilder.add("statusCode", statusCode);
        }

        if (payload.containsKey(CLIENT_CONTEXT)) {
            final String clientContext = payload.getString(CLIENT_CONTEXT);
            jsonObjectBuilder.add(CLIENT_CONTEXT, clientContext);
        }

        final JsonObject notificationFailedPayload = jsonObjectBuilder
                .build();

        final MetadataBuilder builder = metadataFrom(notificationFailedEvent.metadata());
        final Metadata metadata = builder
                .withName(NOTIFICATION_MARK_AS_FAILED_PUBLIC_EVENT)
                .build();

        sender.send(envelopeFrom(metadata, notificationFailedPayload));
    }

    @Handles("notificationnotify.events.poca-email-notification-received")
    public void pocaEmailReceived(final Envelope<PocaEmailNotificationReceived> notificationReceivedEnvelope) {
        final MetadataBuilder builder = metadataFrom(notificationReceivedEnvelope.metadata());
        final Metadata metadata = builder
                .withName(PUBLIC_NOTIFICATIONNOTIFY_POCA_EMAIL_RECEIVED_NOTIFICATION)
                .build();
        sender.send(envelopeFrom(metadata, notificationReceivedEnvelope.payload()));
    }


    @Handles("notificationnotify.events.poca-email-already-received")
    public void pocaEmailAlreadyReceived(final Envelope<PocaEmailAlreadyReceived> emailAlreadyReceivedEnvelope) {
        final UUID pocaFileId = emailAlreadyReceivedEnvelope.payload().getPocaFileId();
        try {
            blobContainerClient.getBlobClient(BLOB_PATH.blobName(pocaFileId)).deleteIfExists();
        } catch (final RuntimeException e) {
            LOGGER.error("Failed to delete blob for pocaFileId='{}' — assuming it does not exist or was already deleted", pocaFileId, e);
        }
    }

    @Handles("public.referencedata.event.reference-data-file-published")
    public void referenceDataFilePublished(final JsonEnvelope event) {
        final JsonObject payload = event.payloadAsJsonObject();
        final UUID correlationId = UUID.fromString(payload.getString("correlationId"));
        final String sourceBlobUri = payload.getString("sourceBlobUri");
        final String recipientEmail = payload.getString("recipientEmail");
        final String subject = payload.getString("subject");
        final String filename = payload.getString("filename");
        blobFileEmailSender.sendEmailWithBlobAttachment(correlationId, sourceBlobUri, recipientEmail, subject, filename);
    }
}
