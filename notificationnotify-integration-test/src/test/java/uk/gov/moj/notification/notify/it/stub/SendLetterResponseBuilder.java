package uk.gov.moj.notification.notify.it.stub;

import static javax.json.Json.createObjectBuilder;
import static javax.ws.rs.core.Response.Status.CREATED;

import java.util.UUID;

import javax.ws.rs.core.Response;

public class SendLetterResponseBuilder implements NotifyResponseBuilder {

    private UUID notificationId;
    private UUID reference;
    private final Response.Status statusCode = CREATED;

    public static SendLetterResponseBuilder sendLetterNotificationResponse(
            final UUID cppNotificationId,
            final UUID govNotifyNotificationId) {
        return new SendLetterResponseBuilder()
                .withReference(cppNotificationId)
                .withNotificationId(govNotifyNotificationId);
    }

    public SendLetterResponseBuilder withNotificationId(final UUID notificationId) {
        this.notificationId = notificationId;
        return this;
    }

    public SendLetterResponseBuilder withReference(final UUID reference) {
        this.reference = reference;
        return this;
    }

    @Override
    public Response.Status getStatus() {
        return statusCode;
    }

    @Override
    public String build() {
        return createObjectBuilder()
                .add("id", notificationId.toString())
                .add("reference", reference.toString())
                .build().toString();
    }
}
