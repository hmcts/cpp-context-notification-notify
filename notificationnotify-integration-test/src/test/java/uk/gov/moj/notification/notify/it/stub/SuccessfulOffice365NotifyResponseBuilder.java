package uk.gov.moj.notification.notify.it.stub;

import static javax.json.Json.createObjectBuilder;
import static javax.ws.rs.core.Response.Status.CREATED;

import java.util.UUID;

import javax.ws.rs.core.Response;

public class SuccessfulOffice365NotifyResponseBuilder implements NotifyResponseBuilder {


    private UUID notificationId;

    private final Response.Status statusCode = CREATED;

    public static SuccessfulOffice365NotifyResponseBuilder successfulOffice365NotifyResponseBuilder() {
        return new SuccessfulOffice365NotifyResponseBuilder();
    }

    public SuccessfulOffice365NotifyResponseBuilder withNotificationId(final UUID notificationId) {
        this.notificationId = notificationId;
        return this;
    }

    @Override
    public Response.Status getStatus() {
        return statusCode;
    }

    public String build() {
        return createObjectBuilder()
                .add("id", notificationId.toString())
                .build().toString();
    }
}
