package uk.gov.moj.notification.notify.it.stub;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

import java.util.UUID;

import javax.json.JsonObjectBuilder;
import javax.ws.rs.core.Response;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

public class SendLetterStub {

    private static final String URL_PATH = "/v2/notifications/letter";

    private UUID reference;
    private String content;
    private String postage;
    private String urlPath = URL_PATH;
    private String responseBody;

    private NotifyResponseBuilder notifyResponseBuilder;

    public static SendLetterStub stubSendLetter() {
        return new SendLetterStub();
    }

    public SendLetterStub withReference(final UUID reference) {
        this.reference = reference;
        return this;
    }

    public SendLetterStub withContent(final String content) {
        this.content = content;
        return this;
    }

    public SendLetterStub withPostage(final String postage) {
        this.postage = postage;
        return this;
    }


    public SendLetterStub withResponseBody(final String responseBody) {
        this.responseBody = responseBody;
        return this;
    }

    public SendLetterStub withUrlPathContaining(final String notificationId) {
        this.urlPath = URL_PATH + notificationId;
        return this;
    }

    public SendLetterStub returning(final NotifyResponseBuilder notifyResponseBuilder) {
        this.notifyResponseBuilder = notifyResponseBuilder;
        return this;
    }

    public void build() {

        final String requestBody = createRequestBody();
        final String responseBody = notifyResponseBuilder.build();

        final Response.Status status = notifyResponseBuilder.getStatus();
        final ResponseDefinitionBuilder responseDefBuilder = aResponse()
                .withStatus(status.getStatusCode());

        if (notifyResponseBuilder instanceof ErrorResponseBuilder) {
            final ErrorResponseBuilder errorResponseBuilder = (ErrorResponseBuilder) notifyResponseBuilder;
            responseDefBuilder.withBody(errorResponseBuilder.getMessage());

        }
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
                .add("content", content);
        if (postage != null) {
            requestBodyBuilder.add("postage", postage);
        }

        return requestBodyBuilder.build().toString();
    }
}
