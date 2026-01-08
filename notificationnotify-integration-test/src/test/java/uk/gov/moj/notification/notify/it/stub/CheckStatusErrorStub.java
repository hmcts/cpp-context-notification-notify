package uk.gov.moj.notification.notify.it.stub;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

import javax.ws.rs.core.Response;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

public class CheckStatusErrorStub {

    private static final String URL_PATH = "/v2/notifications/";
    private String urlPath = URL_PATH;

    private ErrorResponseBuilder errorResponseBuilder;

    public static CheckStatusErrorStub stubGovNotifyCheckStatusError() {
        return new CheckStatusErrorStub();
    }

    public CheckStatusErrorStub returning(final ErrorResponseBuilder errorResponseBuilder) {
        this.errorResponseBuilder = errorResponseBuilder;
        return this;
    }

    public CheckStatusErrorStub withUrlPathContaining(final String notificationId) {
        this.urlPath = URL_PATH + notificationId;
        return this;
    }

    public void build() {
        final Response.Status status = errorResponseBuilder.getStatus();
        final ResponseDefinitionBuilder responseDefBuilder = aResponse()
                .withStatus(status.getStatusCode());
        responseDefBuilder.withBody(errorResponseBuilder.getMessage());
        stubFor(get(urlPathMatching(urlPath))
                .willReturn(responseDefBuilder));
    }
}
