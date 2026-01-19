package uk.gov.moj.notification.notify.it.stub;

import static java.lang.String.format;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static javax.ws.rs.core.Response.Status.CREATED;

import java.util.UUID;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.joda.time.DateTime;

public class CheckStatusResponseBuilder implements NotifyResponseBuilder {

    private static final String TEMPLATE_URI = "https://api.notifications.service.gov.uk/services/%s/templates/%s";
    private Response.Status httpStatus = CREATED;
    private String status = "delivered";

    private int templateVersion = 1;
    private UUID userId = fromString("d43135e4-fff3-45df-9a7e-bc7018a4a589");
    private UUID id;
    private UUID reference;
    private UUID templateId = randomUUID();
    private String emailAddress = "fred.bloggs@gerritt.com";
    private String phoneNumber = "123456778";
    private String line1 = "line1";
    private String line2 = "line2";
    private String line3 = "line3";
    private String line4 = "line4";
    private String line5 = "line5";
    private String line6 = "line6";
    private String postcode = "postcode";
    private String subject = "subject";
    private String body = "body";
    private DateTime createdAt = DateTime.now();
    private DateTime sentAt = DateTime.now();
    private DateTime completedAt = DateTime.now();
    private DateTime estimatedDelivery = DateTime.now();

    private String type = "";


    public static CheckStatusResponseBuilder aNotificationResponse(
            final UUID cppNotificationId,
            final UUID govNotifyNotificationId) {
        return new CheckStatusResponseBuilder().withReference(cppNotificationId).withId(govNotifyNotificationId);
    }

    public CheckStatusResponseBuilder withId(final UUID id) {
        this.id = id;
        return this;
    }

    public CheckStatusResponseBuilder withReference(final UUID reference) {
        this.reference = reference;
        return this;
    }

    public CheckStatusResponseBuilder withStatus(final String status) {
        this.status = status;
        return this;
    }

    @Override
    public Status getStatus() {
        return httpStatus;
    }

    @Override
    public String build() {
        return createObjectBuilder()
                .add("id", id.toString())
                .add("reference", reference.toString())
                .add("emailAddress", emailAddress)
                .add("phoneNumber", phoneNumber)
                .add("line1", line1)
                .add("line2", line2)
                .add("line3", line3)
                .add("line4", line4)
                .add("line5", line5)
                .add("line6", line6)
                .add("postcode", postcode)
                .add("type", type)
                .add("template", createObjectBuilder()
                        .add("id", randomUUID().toString())
                        .add("uri", format(TEMPLATE_URI, userId, templateId))
                        .add("version", templateVersion)
                )
                .add("body", body)
                .add("subject", subject)
                .add("status", status)
                .add("created_at", createdAt.toString())
                .add("sent_at", sentAt.toString())
                .add("completed_at", completedAt.toString())
                .add("estimated_delivery", estimatedDelivery.toString())
                .build().toString();
    }
}
