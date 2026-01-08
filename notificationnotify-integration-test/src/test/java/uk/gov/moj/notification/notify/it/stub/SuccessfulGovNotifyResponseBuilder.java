package uk.gov.moj.notification.notify.it.stub;

import static java.lang.String.format;
import static java.util.Optional.empty;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static javax.json.Json.createObjectBuilder;
import static javax.json.JsonValue.NULL;
import static javax.ws.rs.core.Response.Status.CREATED;

import java.util.Optional;
import java.util.UUID;

import javax.json.JsonObjectBuilder;
import javax.ws.rs.core.Response;

public class SuccessfulGovNotifyResponseBuilder implements NotifyResponseBuilder {

    private static final String TEMPLATE_URI = "https://api.notifications.service.gov.uk/services/%s/templates/%s";
    private static final String NOTIFICATION_URI = "https://api.notifications.service.gov.uk/v2/notifications/%s";
    private UUID reference;
    private UUID templateId = randomUUID();
    private int templateVersion = 1;
    private final UUID  userId = fromString("d43135e4-fff3-45df-9a7e-bc7018a4a589");

    private UUID notificationId;

    private Optional<UUID> replyToAddressId = empty();
    private String subject = "Default reply subject";
    private String body = "Default reply body";
    private final Response.Status statusCode = CREATED;

    public static SuccessfulGovNotifyResponseBuilder aSuccessfulResponse() {
        return new SuccessfulGovNotifyResponseBuilder();
    }

    public SuccessfulGovNotifyResponseBuilder withReference(final UUID reference) {
        this.reference = reference;
        return this;
    }

    public SuccessfulGovNotifyResponseBuilder withTemplateId(final UUID templateId) {
        this.templateId = templateId;
        return this;
    }

    public SuccessfulGovNotifyResponseBuilder withTemplateVersion(final int templateVersion) {
        this.templateVersion = templateVersion;
        return this;
    }

    public SuccessfulGovNotifyResponseBuilder withEmailSubject(final String subject) {
        this.subject = subject;
        return this;
    }

    public SuccessfulGovNotifyResponseBuilder withEmailBody(final String body) {
        this.body = body;
        return this;
    }

    public SuccessfulGovNotifyResponseBuilder withNotificationId(final UUID notificationId) {
        this.notificationId = notificationId;
        return this;
    }

    public SuccessfulGovNotifyResponseBuilder withReplyToAddressId(final Optional<UUID> replyToAddressId) {
        this.replyToAddressId = replyToAddressId;
        return this;
    }

    @Override
    public Response.Status getStatus() {
        return statusCode;
    }

    public String build() {
        final JsonObjectBuilder contentBuilder = createObjectBuilder()
                .add("body", body)
                .add("subject", subject);

        replyToAddressId.ifPresent(s -> contentBuilder.add("from_email", s.toString()));

        return createObjectBuilder()
                .add("content", contentBuilder)
                .add("id", notificationId.toString())
                .add("reference", reference.toString())
                .add("scheduled_for", NULL)
                .add("template", createObjectBuilder()
                        .add("id", templateId.toString())
                        .add("uri", format(TEMPLATE_URI, userId, templateId))
                        .add("version", templateVersion)
                )
                .add("uri", format(NOTIFICATION_URI, notificationId))
                .build().toString();
    }
}
