package uk.gov.moj.notification.notify.it.stub;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

public class CheckStatusStub {

    private static final String URL_PATH = "/v2/notifications/";
    private String urlPath = URL_PATH;

    private CheckStatusResponseBuilder checkStatusNotifyResponseBuilder;

    public static CheckStatusStub stubGovNotifyCheckStatus() {
        return new CheckStatusStub();
    }

    public CheckStatusStub returning(final CheckStatusResponseBuilder notifyResponseBuilder) {
        this.checkStatusNotifyResponseBuilder = notifyResponseBuilder;
        return this;
    }

    public CheckStatusStub withUrlPathContaining(final String notificationId) {
        this.urlPath = URL_PATH + notificationId;
        return this;
    }

    public void build() {

        final String responseBody = checkStatusNotifyResponseBuilder.build();
        final ResponseDefinitionBuilder responseDefBuilder = aResponse().withBody(responseBody);
        stubFor(get(urlPathEqualTo(urlPath))
                .willReturn(responseDefBuilder));
    }
}
