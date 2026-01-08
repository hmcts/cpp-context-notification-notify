package uk.gov.moj.notification.notify.it.stub;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static java.util.Optional.empty;
import static java.util.UUID.randomUUID;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

public class CheckStatusStubMultipleResponse {

    private static final String URL_PATH = "/v2/notifications/";


    private UUID reference ;
    private UUID templateId = randomUUID();
    private String emailAddress = "fred.bloggs@gerritt.com";
    private Optional<UUID> replyToAddressId = empty();
    private String urlPath = URL_PATH;
    private String scenarioState = STARTED;
    private String scenario;
    private String nextScenarioState;

    private final Map<String, Object> personalisationMap = new HashMap<>();

    private CheckStatusResponseBuilder checkStatusNotifyResponseBuilder;

    public static CheckStatusStubMultipleResponse stubGovNotifyCheckStatusForMultipleResponse() {
        return new CheckStatusStubMultipleResponse();
    }

    public CheckStatusStubMultipleResponse returning(final CheckStatusResponseBuilder notifyResponseBuilder) {
        this.checkStatusNotifyResponseBuilder = notifyResponseBuilder;
        return this;
    }

    public CheckStatusStubMultipleResponse withUrlPathContaining(final String notificationId) {
        this.urlPath = URL_PATH + notificationId;
        return this;
    }

    public CheckStatusStubMultipleResponse withScenarioState(final String scenarioState) {
        this.scenarioState = scenarioState;
        return this;
    }

    public CheckStatusStubMultipleResponse withScenario(final String scenario) {
        this.scenario = scenario;
        return this;
    }

    public CheckStatusStubMultipleResponse withNextScenarioState(final String nextScenarioState) {
        this.nextScenarioState = nextScenarioState;
        return this;
    }

    public void build() {

        final String responseBody = checkStatusNotifyResponseBuilder.build();
        final ResponseDefinitionBuilder responseDefBuilder = aResponse().withBody(responseBody);
        stubFor(get(urlPathEqualTo(urlPath))
                .inScenario(scenario)
                .whenScenarioStateIs(scenarioState)
                .willSetStateTo(nextScenarioState)
                .willReturn(responseDefBuilder));
    }
}
