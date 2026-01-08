package uk.gov.moj.notification.notify.it;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.reset;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static javax.json.Json.createObjectBuilder;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.justice.services.common.http.HeaderConstants.USER_ID;
import static uk.gov.justice.services.test.utils.common.host.TestHostProvider.getHost;
import static uk.gov.justice.services.test.utils.core.http.PollingRequestParamsBuilder.pollingRequestParams;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.DOCUMENT_DOWNLOAD_URL;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubDocumentDownload;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubEnableAllCapabilities;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyCheckLetterStatusWithStatusReceived;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyCheckStatusWhenPermanentFailure;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyCheckStatusWithStatusDelivered;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyPermanentFailureForSendLetter;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifySendLetterWithStatusDelivered;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifySuccessClientWithPersonalisationWithoutMaterialUrl;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubUsersGroups;
import static uk.gov.moj.notification.notify.it.util.PayloadGeneratorUtil.payloadWithPersonalisationWithoutMaterialUrl;
import static uk.gov.moj.notification.notify.it.util.ResourceLoader.getBase64EncodedFileContent;
import static uk.gov.moj.notification.notify.it.util.ResourceLoader.getFileContent;

import uk.gov.justice.services.common.converter.ZonedDateTimes;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.test.utils.core.http.PollingRequestParams;
import uk.gov.justice.services.test.utils.core.http.PollingRestClient;
import uk.gov.justice.services.test.utils.core.rest.RestClient;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueryIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueryIT.class);
    private static final String HOST = System.getProperty("INTEGRATION_HOST_KEY", "localhost");
    private static final int PORT = 8080;

    private static final String SEND_NOTIFICATION_POST_URL = format("http://%s:%s/notificationnotify-command-api/command/api/rest/notificationnotify/notifications/", HOST, PORT);
    private static final String NOTIFY_SYSTEM_USER = randomUUID().toString();
    private final static String EMAIL_ADDRESS = "fred.bloggs@acme.com";
    private static final String NOTIFICATION_TYPE_LETTER = "LETTER";
    private static final String PDF_FILE_PATH = "pdf/JohnBloggs.pdf";

    private final RestClient restClient = new RestClient();
    private MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
    private static final String SEND_EMAIL_POST_CONTENT_TYPE = "application/vnd.notificationnotify.email+json";
    private static final String SEND_LETTER_POST_CONTENT_TYPE = "application/vnd.notificationnotify.letter+json";
    private static final String NOTIFICATION_BY_ID_CONTENT_TYPE = "application/vnd.notificationnotify.query.notification+json";
    private static final String NOTIFICATIONS_CONTENT_TYPE = "application/vnd.notificationnotify.query.find-notification+json";
    private static final String STATUS_QUEUED = "QUEUED";
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_FAILED = "FAILED";
    private static final String QUERY_NOTIFICATION_URL = format("http://%s:%s/notificationnotify-query-api/query/api/rest/notificationnotify/", HOST, PORT);
    private static final String QUERY_NOTIFICATIONS_URL = QUERY_NOTIFICATION_URL + "notification?status=%s&createdAfter=%s&sendToAddress=%s";
    private final PollingRestClient pollingRestClient = new PollingRestClient();

    @BeforeAll
    public static void beforeAll() {
        configureFor(getHost(), PORT);
        reset();
    }

    @BeforeEach
    public void before() throws Exception {
        stubEnableAllCapabilities();
        stubUsersGroups();
        headers.add(USER_ID, NOTIFY_SYSTEM_USER);
    }

    @Test
    public void shouldRetrievePreviouslySentNotificationByNotificationId() {
        final UUID notificationId = randomUUID();

        sendAndAssertEmailNotification(notificationId);

        final String url = QUERY_NOTIFICATION_URL + "notification/" + notificationId.toString();

        pollForResponseAfterSentNotificationProcessed(
                url,
                NOTIFICATION_BY_ID_CONTENT_TYPE,
                json -> json.contains(notificationId.toString()));

        final Response queryResponse = restClient.query(url, NOTIFICATION_BY_ID_CONTENT_TYPE, headers);

        assertEquals(OK.getStatusCode(), queryResponse.getStatus());

        final JsonPath responsePayload = new JsonPath(queryResponse.readEntity(String.class));
        assertThat(responsePayload.getString("notificationId"), equalTo(notificationId.toString()));
        assertThat(responsePayload.getString("dateCreated"), is(notNullValue()));
        assertThat(responsePayload.getString("lastUpdated"), is(notNullValue()));
        assertThat(responsePayload.getString("status"), is(STATUS_QUEUED));
    }

    @Test
    public void shouldRetrievePreviouslySentNotificationsBySearchCriteria() {
        final UUID cppNotificationId = randomUUID();
        final UUID govNotifyNotificationId = randomUUID();

        stubGovNotifySuccessClientWithPersonalisationWithoutMaterialUrl(cppNotificationId, govNotifyNotificationId);
        stubGovNotifyCheckStatusWithStatusDelivered(cppNotificationId, govNotifyNotificationId);

        sendAndAssertEmailNotification(cppNotificationId);

        final String url = format(QUERY_NOTIFICATION_URL + "notification?status=%s", STATUS_SENT);
        final Predicate<String> jsonContainsNotification = jsonContainsNotificationWith(cppNotificationId, STATUS_SENT);

        pollForResponseAfterSentNotificationProcessed(url, NOTIFICATIONS_CONTENT_TYPE, jsonContainsNotification);

        final Response queryResponse = restClient.query(url, NOTIFICATIONS_CONTENT_TYPE, headers);

        assertEquals(OK.getStatusCode(), queryResponse.getStatus());

        final JsonPath responsePayload = new JsonPath(queryResponse.readEntity(String.class));

        final List<Map> notifications = responsePayload.getJsonObject("notifications");

        final Optional<Map> actualResultOptional = notifications.stream()
                .filter(map -> map.get("notificationId").equals(cppNotificationId.toString()))
                .findFirst();

        assertThat(actualResultOptional.isPresent(), is(true));
        final Map actualResult = actualResultOptional.get();

        assertThat(actualResult.get("notificationId"), is(cppNotificationId.toString()));
        assertThat(actualResult.get("status"), is(STATUS_SENT));
    }

    @Test
    public void shouldRetrievePreviouslySentLetterNotificationsBySearchCriteria() throws IOException {
        final UUID cppNotificationId = randomUUID();
        final UUID govNotifyNotificationId = randomUUID();

        stubDocumentDownload(getFileContent(PDF_FILE_PATH));
        stubGovNotifySendLetterWithStatusDelivered(cppNotificationId, govNotifyNotificationId, getBase64EncodedFileContent(PDF_FILE_PATH));
        stubGovNotifyCheckLetterStatusWithStatusReceived(cppNotificationId, govNotifyNotificationId);

        sendAndAssertLetterNotification(cppNotificationId);

        final String url = format(QUERY_NOTIFICATION_URL + "notification?status=%s", STATUS_SENT);
        final Predicate<String> jsonContainsNotification = jsonContainsNotificationWith(cppNotificationId, STATUS_SENT);

        pollForResponseAfterSentNotificationProcessed(url, NOTIFICATIONS_CONTENT_TYPE, jsonContainsNotification);

        final Response queryResponse = restClient.query(url, NOTIFICATIONS_CONTENT_TYPE, headers);

        assertEquals(OK.getStatusCode(), queryResponse.getStatus());

        final JsonPath responsePayload = new JsonPath(queryResponse.readEntity(String.class));

        final List<Map> notifications = responsePayload.getJsonObject("notifications");

        final Optional<Map> actualResultOptional = notifications.stream()
                .filter(map -> map.get("notificationId").equals(cppNotificationId.toString()))
                .findFirst();

        assertThat(actualResultOptional.isPresent(), is(true));
        final Map actualResult = actualResultOptional.get();

        assertThat(actualResult.get("notificationId"), is(cppNotificationId.toString()));
        assertThat(actualResult.get("status"), is(STATUS_SENT));
    }

    @Test
    public void shouldRetrievePreviouslyFailedLetterNotificationsBySearchCriteria() throws IOException {
        final UUID cppNotificationId = randomUUID();

        stubDocumentDownload(getFileContent(PDF_FILE_PATH));
        stubGovNotifyPermanentFailureForSendLetter(cppNotificationId, getBase64EncodedFileContent(PDF_FILE_PATH));

        sendAndAssertLetterNotification(cppNotificationId);

        final String url = format(QUERY_NOTIFICATION_URL + "notification?status=%s", STATUS_FAILED);
        final Predicate<String> jsonContainsNotification = jsonContainsNotificationWith(cppNotificationId, STATUS_FAILED);

        pollForResponseAfterSentNotificationProcessed(url, NOTIFICATIONS_CONTENT_TYPE, jsonContainsNotification);

        final Response queryResponse = restClient.query(url, NOTIFICATIONS_CONTENT_TYPE, headers);

        assertEquals(OK.getStatusCode(), queryResponse.getStatus());

        final JsonPath responsePayload = new JsonPath(queryResponse.readEntity(String.class));

        final List<Map> notifications = responsePayload.getJsonObject("notifications");

        final Optional<Map> actualResultOptional = notifications.stream()
                .filter(map -> map.get("notificationId").equals(cppNotificationId.toString()))
                .findFirst();

        assertThat(actualResultOptional.isPresent(), is(true));
        final Map actualResult = actualResultOptional.get();

        assertThat(actualResult.get("notificationId"), is(cppNotificationId.toString()));
        assertThat(actualResult.get("status"), is(STATUS_FAILED));
    }

    @Test
    public void shouldRetrieveFailedNotificationBySearchCriteria() {
        final UUID cppNotificationId = randomUUID();
        final UUID govNotifyNotificationId = randomUUID();

        stubGovNotifySuccessClientWithPersonalisationWithoutMaterialUrl(cppNotificationId, govNotifyNotificationId);
        stubGovNotifyCheckStatusWhenPermanentFailure(cppNotificationId, govNotifyNotificationId);

        final ZonedDateTime now = new UtcClock().now();

        final String createdAfter = ZonedDateTimes.toString(now.minusSeconds(100));
        sendAndAssertEmailNotification(cppNotificationId);

        final String url = format(QUERY_NOTIFICATIONS_URL, STATUS_FAILED, createdAfter, EMAIL_ADDRESS);
        final Predicate<String> jsonContainsNotification = jsonContainsNotificationWith(cppNotificationId, STATUS_FAILED);

        pollForResponseAfterSentNotificationProcessed(url, NOTIFICATIONS_CONTENT_TYPE, jsonContainsNotification);

        final Response queryResponse = restClient.query(url, NOTIFICATIONS_CONTENT_TYPE, headers);

        assertEquals(OK.getStatusCode(), queryResponse.getStatus());

        final JsonPath responsePayload = new JsonPath(queryResponse.readEntity(String.class));

        final List<Map> notifications = responsePayload.getJsonObject("notifications");

        final Optional<Map> actualResultOptional = notifications.stream()
                .filter(map -> map.get("notificationId").equals(cppNotificationId.toString()))
                .findFirst();

        assertThat(actualResultOptional.isPresent(), is(true));
        final Map actualResult = actualResultOptional.get();

        assertThat(actualResult.get("notificationId"), is(cppNotificationId.toString()));
        assertThat(actualResult.get("status"), is(STATUS_FAILED));
    }

    @Test
    public void shouldRetrievePreviouslySentLetterNotificationsByNotificationType() throws IOException {
        final UUID cppNotificationId = randomUUID();
        final UUID govNotifyNotificationId = randomUUID();

        stubDocumentDownload(getFileContent(PDF_FILE_PATH));
        stubGovNotifySendLetterWithStatusDelivered(cppNotificationId, govNotifyNotificationId, getBase64EncodedFileContent(PDF_FILE_PATH));
        stubGovNotifyCheckLetterStatusWithStatusReceived(cppNotificationId, govNotifyNotificationId);

        sendAndAssertLetterNotification(cppNotificationId);

        final String url = format(QUERY_NOTIFICATION_URL + "notification?notificationType=%s", NOTIFICATION_TYPE_LETTER);
        final Predicate<String> jsonContainsNotification = jsonContainsNotificationWith(cppNotificationId, STATUS_SENT);

        pollForResponseAfterSentNotificationProcessed(url, NOTIFICATIONS_CONTENT_TYPE, jsonContainsNotification);

        final Response queryResponse = restClient.query(url, NOTIFICATIONS_CONTENT_TYPE, headers);

        assertEquals(OK.getStatusCode(), queryResponse.getStatus());

        final JsonPath responsePayload = new JsonPath(queryResponse.readEntity(String.class));

        final List<Map> notifications = responsePayload.getJsonObject("notifications");

        final Optional<Map> actualResultOptional = notifications.stream()
                .filter(map -> map.get("notificationId").equals(cppNotificationId.toString()))
                .findFirst();

        assertThat(actualResultOptional.isPresent(), is(true));
        final Map actualResult = actualResultOptional.get();

        assertThat(actualResult.get("notificationId"), is(cppNotificationId.toString()));
        assertThat(actualResult.get("notificationType"), is(NOTIFICATION_TYPE_LETTER));
        assertThat(actualResult.get("status"), is(STATUS_SENT));
    }

    @Test
    public void shouldRetrievePreviouslySentLetterNotificationsByLetterUrl() throws IOException {
        final UUID cppNotificationId = randomUUID();
        final UUID govNotifyNotificationId = randomUUID();

        stubDocumentDownload(getFileContent(PDF_FILE_PATH));
        stubGovNotifySendLetterWithStatusDelivered(cppNotificationId, govNotifyNotificationId, getBase64EncodedFileContent(PDF_FILE_PATH));
        stubGovNotifyCheckLetterStatusWithStatusReceived(cppNotificationId, govNotifyNotificationId);

        sendAndAssertLetterNotification(cppNotificationId);

        final String url = format(QUERY_NOTIFICATION_URL + "notification?letterUrl=%s", DOCUMENT_DOWNLOAD_URL);
        final Predicate<String> jsonContainsNotification = jsonContainsNotificationWith(cppNotificationId, STATUS_SENT);

        pollForResponseAfterSentNotificationProcessed(url, NOTIFICATIONS_CONTENT_TYPE, jsonContainsNotification);

        final Response queryResponse = restClient.query(url, NOTIFICATIONS_CONTENT_TYPE, headers);

        assertEquals(OK.getStatusCode(), queryResponse.getStatus());

        final JsonPath responsePayload = new JsonPath(queryResponse.readEntity(String.class));

        final List<Map> notifications = responsePayload.getJsonObject("notifications");

        final Optional<Map> actualResultOptional = notifications.stream()
                .filter(map -> map.get("notificationId").equals(cppNotificationId.toString()))
                .findFirst();

        assertThat(actualResultOptional.isPresent(), is(true));
        final Map actualResult = actualResultOptional.get();

        assertThat(actualResult.get("notificationId"), is(cppNotificationId.toString()));
        assertThat(actualResult.get("letterUrl"), is(DOCUMENT_DOWNLOAD_URL));
        assertThat(actualResult.get("status"), is(STATUS_SENT));
    }

    private void pollForResponseAfterSentNotificationProcessed(final String url,
                                                               final String mediaType,
                                                               final Predicate<String> stringPredicate) {
        final PollingRequestParams pollingRequestParams =
                pollingRequestParams(url, mediaType)
                        .withResponseBodyCondition(stringPredicate)
                        .withExpectedResponseStatus(OK)
                        .withHeader(USER_ID, NOTIFY_SYSTEM_USER)
                        .withDelayInMillis(1000L)
                        .withRetryCount(60)
                        .build();

        pollingRestClient.pollUntilExpectedResponse(pollingRequestParams);
    }

    private Predicate<String> jsonContainsNotificationWith(final UUID notificationId, final String status) {
        return json -> {
            final JsonPath responsePayload = new JsonPath(json);
            final List<Map> notifications = responsePayload.getJsonObject("notifications");

            return notifications.stream()
                    .anyMatch(notification ->
                            notification.get("notificationId").equals(notificationId.toString()) &&
                                    notification.get("status").equals(status));
        };
    }

    private void sendAndAssertEmailNotification(final UUID notificationId) {
        final Response response = sendNotificationJson(notificationId.toString(), SEND_EMAIL_POST_CONTENT_TYPE, payloadWithPersonalisationWithoutMaterialUrl());

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        LOGGER.info("Response received " + response.getStatus());
    }

    private void sendAndAssertLetterNotification(final UUID notificationId) {
        final String payload = createObjectBuilder().add("letterUrl", DOCUMENT_DOWNLOAD_URL)
                .build().toString();
        final Response response = sendNotificationJson(notificationId.toString(), SEND_LETTER_POST_CONTENT_TYPE, payload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        LOGGER.info("Response received " + response.getStatus());
    }

    private Response sendNotificationJson(final String resource,
                                          final String mediaType,
                                          final String payload) {
        LOGGER.info("URL" + SEND_NOTIFICATION_POST_URL + resource);
        final MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add(USER_ID, NOTIFY_SYSTEM_USER);
        return restClient.postCommand(SEND_NOTIFICATION_POST_URL + resource, mediaType, payload, headers);
    }
}
