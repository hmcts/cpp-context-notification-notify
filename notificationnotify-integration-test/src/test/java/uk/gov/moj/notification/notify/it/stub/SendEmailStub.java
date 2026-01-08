package uk.gov.moj.notification.notify.it.stub;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static uk.gov.moj.notification.notify.it.util.PayloadGeneratorUtil.getPersonalisationJsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.json.JsonObjectBuilder;
import javax.ws.rs.core.Response;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

public class SendEmailStub {

    private static final String URL_PATH = "/v2/notifications/email";

    private UUID reference;
    private UUID templateId;
    private String emailAddress = "fred.bloggs@gerritt.com";
    private Optional<UUID> replyToAddressId = empty();
    private final String urlPath = URL_PATH;

    private final Map<String, Object> personalisationMap = new HashMap<>();

    private NotifyResponseBuilder notifyResponseBuilder;

    public static SendEmailStub sendEmailStub() {
        return new SendEmailStub();
    }

    public SendEmailStub withReference(final UUID reference) {
        this.reference = reference;
        return this;
    }

    public SendEmailStub withEmailAddress(final String emailAddress) {
        this.emailAddress = emailAddress;
        return this;
    }

    public SendEmailStub withTemplateId(final UUID templateId) {
        this.templateId = templateId;
        return this;
    }

    public SendEmailStub withPersonalization(final String name, final Object value) {
        personalisationMap.put(name, value);
        return this;
    }

    public SendEmailStub withReplyToAddressId(final UUID replyToAddressId) {
        this.replyToAddressId = of(replyToAddressId);
        return this;
    }

    public SendEmailStub returning(final NotifyResponseBuilder notifyResponseBuilder) {
        this.notifyResponseBuilder = notifyResponseBuilder;
        return this;
    }

    public void build() {

        final String requestBody = createRequestBody();
        final String responseBody = notifyResponseBuilder.build();

        final Response.Status status = notifyResponseBuilder.getStatus();
        final ResponseDefinitionBuilder responseDefBuilder = aResponse()
                .withStatus(status.getStatusCode());

        if (!status.equals(INTERNAL_SERVER_ERROR)) {
            responseDefBuilder.withBody(responseBody);
        }

        stubFor(post(urlPathEqualTo(urlPath))
                .withHeader("Authorization", matching("^Bearer .*$"))
                .withHeader("User-Agent", equalTo("NOTIFY-API-JAVA-CLIENT/3.17.3-RELEASE"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withHeader("Accept", equalTo("application/json"))
                .withRequestBody(equalToJson(requestBody))
                .willReturn(responseDefBuilder));
    }

    private String createRequestBody() {
        final JsonObjectBuilder requestBodyBuilder = createObjectBuilder()
                .add("reference", reference.toString())
                .add("email_address", emailAddress)
                .add("template_id", templateId.toString());

        replyToAddressId.ifPresent(s -> requestBodyBuilder.add("email_reply_to_id", s.toString()));

        if (!personalisationMap.isEmpty()) {
            requestBodyBuilder.add("personalisation", getPersonalisationJsonObject(personalisationMap));
        }
        return requestBodyBuilder.build().toString();
    }
}
