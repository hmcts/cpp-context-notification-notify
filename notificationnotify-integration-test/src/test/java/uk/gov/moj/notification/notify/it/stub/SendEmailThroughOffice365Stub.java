package uk.gov.moj.notification.notify.it.stub;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.ws.rs.core.Response;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

public class SendEmailThroughOffice365Stub {

    private static final String URL_PATH = "/workflows/.*";
    private static final String MATERIAL_DOCUMENT_URL = "/somewhere/letter/90fe9fe3-0e03-40c3-8298-ec6f55323582";
    private static final String MATERIAL_URL = "http://localhost:8080" + MATERIAL_DOCUMENT_URL;

    private UUID reference;
    private UUID templateId;
    private String emailAddress = "fred.bloggs@gerritt.com";
    private Optional<UUID> replyToAddressId = empty();
    private final String urlPath = URL_PATH;

    private final Map<String, Object> personalisationMap = new HashMap<>();

    private NotifyResponseBuilder notifyResponseBuilder;

    public static SendEmailThroughOffice365Stub sendEmailThroughOffice365Stub() {
        return new SendEmailThroughOffice365Stub();
    }

    public SendEmailThroughOffice365Stub withReference(final UUID reference) {
        this.reference = reference;
        return this;
    }

    public SendEmailThroughOffice365Stub withEmailAddress(final String emailAddress) {
        this.emailAddress = emailAddress;
        return this;
    }

    public SendEmailThroughOffice365Stub withTemplateId(final UUID templateId) {
        this.templateId = templateId;
        return this;
    }

    public SendEmailThroughOffice365Stub withPersonalization(final String name, final String value) {
        personalisationMap.put(name, value);
        return this;
    }

    public SendEmailThroughOffice365Stub withReplyToAddressId(final UUID replyToAddressId) {
        this.replyToAddressId = of(replyToAddressId);
        return this;
    }

    public SendEmailThroughOffice365Stub returning(final NotifyResponseBuilder notifyResponseBuilder) {
        this.notifyResponseBuilder = notifyResponseBuilder;
        return this;
    }

    public void build() {

        final String responseBody = notifyResponseBuilder.build();

        final Response.Status status = notifyResponseBuilder.getStatus();
        final ResponseDefinitionBuilder responseDefBuilder = aResponse()
                .withStatus(status.getStatusCode());

        if (!status.equals(INTERNAL_SERVER_ERROR)) {
            responseDefBuilder.withBody(responseBody);
        }

        stubFor(post(urlPathMatching(urlPath))
                .willReturn(responseDefBuilder));

    }
}
