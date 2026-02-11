package uk.gov.moj.notification.notify.it;

import static com.jayway.jsonassert.JsonAssert.with;
import static java.lang.String.format;
import static java.util.Optional.empty;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toList;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.jupiter.api.Assertions.fail;
import static uk.gov.justice.services.common.http.HeaderConstants.USER_ID;
import static uk.gov.justice.services.integrationtest.utils.jms.JmsMessageConsumerClientProvider.newPublicJmsMessageConsumerClientProvider;
import static uk.gov.justice.services.test.utils.common.host.TestHostProvider.getHost;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.CLIENT_CONTEXT;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.DOCUMENT_DOWNLOAD_URL;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubDocumentDownload;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubDocumentDownloadFailure;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubEnableAllCapabilities;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyCheckLetterStatusWithStatusReceived;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyCheckStatusWithStatusUnexpected;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyCheckStatusWithStatusValidationError;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyForLetterAccepted;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyForPendingVirusCheck;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyPermanentFailureForSendLetter;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyPermanentFailureForVirusScannedFailed;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifySendLetterWithStatusDelivered;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifySendLetterWithStatusDeliveredWithFirstClassPostage;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyTemporaryFailureForSendLetter;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubMaterialContent;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubUsersGroups;
import static uk.gov.moj.notification.notify.it.util.FrameworkConstants.PUBLIC_EVENT_FAILED;
import static uk.gov.moj.notification.notify.it.util.FrameworkConstants.PUBLIC_EVENT_SENT;
import static uk.gov.moj.notification.notify.it.util.ResourceLoader.getBase64EncodedFileContent;
import static uk.gov.moj.notification.notify.it.util.ResourceLoader.getFileContent;

import uk.gov.justice.services.eventsourcing.repository.jdbc.event.Event;
import uk.gov.justice.services.integrationtest.utils.jms.JmsMessageConsumerClient;
import uk.gov.justice.services.test.utils.core.messaging.Poller;
import uk.gov.justice.services.test.utils.core.rest.RestClient;
import uk.gov.moj.notification.notify.it.util.EventFetcher;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;

import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NotifyLetterIT extends BaseIT {

    private static final String PORT = "8080";
    private static final String NOTIFY_SYSTEM_USER = randomUUID().toString();
    private static final String SEND_NOTIFICATION_POST_URL = format("http://%s:%s/notificationnotify-command-api/command/api/rest/notificationnotify/notifications/", getHost(), PORT);
    private static final String SEND_LETTER_POST_CONTENT_TYPE = "application/vnd.notificationnotify.letter+json";
    private static final String PDF_FILE_PATH = "pdf/JohnBloggs.pdf";

    private final RestClient restClient = new RestClient();
    private EventFetcher eventFetcher = null;

    private JmsMessageConsumerClient publicNotificationSentConsumerClient;
    private JmsMessageConsumerClient publicNotificationFailedConsumerClient;

    private final Poller poller = new Poller();

    @BeforeEach
    public void setUp() throws Exception {
        stubEnableAllCapabilities();
        stubUsersGroups();
        eventFetcher = new EventFetcher();

        this.publicNotificationSentConsumerClient = newPublicJmsMessageConsumerClientProvider()
                .withEventNames(PUBLIC_EVENT_SENT)
                .getMessageConsumerClient();

        this.publicNotificationFailedConsumerClient = newPublicJmsMessageConsumerClientProvider()
                .withEventNames(PUBLIC_EVENT_FAILED)
                .getMessageConsumerClient();
    }

    @Test
    public void shouldSendLetterSuccessfully() throws Exception {
        final UUID cppNotificationId = randomUUID();
        final UUID govNotifyNotificationId = randomUUID();

        stubDocumentDownload(getFileContent(PDF_FILE_PATH));
        stubGovNotifySendLetterWithStatusDelivered(cppNotificationId, govNotifyNotificationId, getBase64EncodedFileContent(PDF_FILE_PATH));
        stubGovNotifyCheckLetterStatusWithStatusReceived(cppNotificationId, govNotifyNotificationId);

        final String payload = createObjectBuilder()
                .add("letterUrl", DOCUMENT_DOWNLOAD_URL)
                .build().toString();
        final Response response = sendLetterNotificationJson(cppNotificationId.toString(),
                SEND_LETTER_POST_CONTENT_TYPE, payload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final int expectedNumberOfEvents = 2;
        final List<Event> events = eventFetcher.getEventsByStreamAndSize(cppNotificationId, expectedNumberOfEvents);

        assertThat(events.size(), is(expectedNumberOfEvents));

        final Event queuedEvent = events.get(0);
        assertThat(queuedEvent.getName(), is("notificationnotify.events.letter-queued"));
        with(queuedEvent.getMetadata())
                .assertThat("stream.id", is(cppNotificationId.toString()))
                .assertThat("stream.version", is(1));
        with(queuedEvent.getPayload())
                .assertThat("$.notificationId", is(cppNotificationId.toString()))
                .assertThat("$.letterUrl", startsWith("http://localhost:8080/somewhere/letter/"));
        ;

        final Event sentEvent = events.get(1);
        assertThat(sentEvent.getName(), is("notificationnotify.events.notification-sent"));
        with(sentEvent.getMetadata())
                .assertThat("stream.id", is(cppNotificationId.toString()))
                .assertThat("stream.version", is(2));
        with(sentEvent.getPayload())
                .assertThat("$.notificationId", is(cppNotificationId.toString()))
                .assertThat("$.sentTime", is(notNullValue()))
        ;

        final Optional<String> publicSentEvent = getEventByNotificationId(publicNotificationSentConsumerClient, cppNotificationId);

        if (publicSentEvent.isPresent()) {
            final JsonPath jsonPathPublicSentEvent = new JsonPath(publicSentEvent.get());
            assertThat(jsonPathPublicSentEvent.get("notificationId"), is(cppNotificationId.toString()));
            assertThat(jsonPathPublicSentEvent.get("sentTime"), notNullValue());
        } else {
            fail();
        }
    }

    @Test
    public void shouldSendFirstClassLetterSuccessfully() throws Exception {
        final UUID cppNotificationId = randomUUID();
        final UUID govNotifyNotificationId = randomUUID();

        stubDocumentDownload(getFileContent(PDF_FILE_PATH));
        stubGovNotifySendLetterWithStatusDeliveredWithFirstClassPostage(cppNotificationId, govNotifyNotificationId, getBase64EncodedFileContent(PDF_FILE_PATH));
        stubGovNotifyCheckLetterStatusWithStatusReceived(cppNotificationId, govNotifyNotificationId);

        final String payload = createObjectBuilder()
                .add("letterUrl", DOCUMENT_DOWNLOAD_URL)
                .add("postage", "first")
                .add("clientContext", CLIENT_CONTEXT)
                .build().toString();
        final Response response = sendLetterNotificationJson(cppNotificationId.toString(),
                SEND_LETTER_POST_CONTENT_TYPE, payload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final int expectedNumberOfEvents = 2;
        final List<Event> events = eventFetcher.getEventsByStreamAndSize(cppNotificationId, expectedNumberOfEvents);

        assertThat(events.size(), is(expectedNumberOfEvents));

        final Event queuedEvent = events.get(0);
        assertThat(queuedEvent.getName(), is("notificationnotify.events.first-class-letter-queued"));
        with(queuedEvent.getMetadata())
                .assertThat("stream.id", is(cppNotificationId.toString()))
                .assertThat("stream.version", is(1));
        with(queuedEvent.getPayload())
                .assertThat("$.notificationId", is(cppNotificationId.toString()))
                .assertThat("$.letterUrl", startsWith("http://localhost:8080/somewhere/letter/"));
        ;

        final Event sentEvent = events.get(1);
        assertThat(sentEvent.getName(), is("notificationnotify.events.notification-sent"));
        with(sentEvent.getMetadata())
                .assertThat("stream.id", is(cppNotificationId.toString()))
                .assertThat("stream.version", is(2));
        with(sentEvent.getPayload())
                .assertThat("$.notificationId", is(cppNotificationId.toString()))
                .assertThat("$.sentTime", is(notNullValue()))
        ;

        final Optional<String> publicSentEvent = getEventByNotificationId(publicNotificationSentConsumerClient, cppNotificationId);

        if (publicSentEvent.isPresent()) {
            final JsonPath jsonPathPublicSentEvent = new JsonPath(publicSentEvent.get());
            assertThat(jsonPathPublicSentEvent.get("notificationId"), is(cppNotificationId.toString()));
            assertThat(jsonPathPublicSentEvent.get("sentTime"), notNullValue());
            assertThat(jsonPathPublicSentEvent.get("clientContext"), is(CLIENT_CONTEXT));

        } else {
            fail();
        }
    }

    @Test
    public void shouldSendMaterialLetterSuccessfully() throws Exception {
        final UUID cppNotificationId = randomUUID();
        final UUID govNotifyNotificationId = randomUUID();
        final UUID materialId = randomUUID();
        stubMaterialContent(materialId, getFileContent(PDF_FILE_PATH));
        stubGovNotifySendLetterWithStatusDelivered(cppNotificationId, govNotifyNotificationId, getBase64EncodedFileContent(PDF_FILE_PATH));
        stubGovNotifyCheckLetterStatusWithStatusReceived(cppNotificationId, govNotifyNotificationId);

        final String payload = createObjectBuilder()
                .add("letterUrl", "http://localhost:8080/material-query-api/query/api/rest/material/material/" + materialId + "?stream=true&requestPdf=true")
                .build().toString();
        final Response response = sendLetterNotificationJson(cppNotificationId.toString(),
                SEND_LETTER_POST_CONTENT_TYPE, payload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final int expectedNumberOfEvents = 2;
        final List<Event> events = eventFetcher.getEventsByStreamAndSize(cppNotificationId, expectedNumberOfEvents);

        assertThat(events.size(), is(expectedNumberOfEvents));

        final Event queuedEvent = events.get(0);
        assertThat(queuedEvent.getName(), is("notificationnotify.events.letter-queued"));
        with(queuedEvent.getMetadata())
                .assertThat("stream.id", is(cppNotificationId.toString()))
                .assertThat("stream.version", is(1));
        with(queuedEvent.getPayload())
                .assertThat("$.notificationId", is(cppNotificationId.toString()))
                .assertThat("$.letterUrl", startsWith("http://localhost:8080/material-query-api/query/api/rest/material/material/"))
                .assertThat("$.letterUrl", endsWith("?stream=true&requestPdf=true"))
        ;

        final Event sentEvent = events.get(1);
        assertThat(sentEvent.getName(), is("notificationnotify.events.notification-sent"));
        with(sentEvent.getMetadata())
                .assertThat("stream.id", is(cppNotificationId.toString()))
                .assertThat("stream.version", is(2));
        with(sentEvent.getPayload())
                .assertThat("$.notificationId", is(cppNotificationId.toString()))
                .assertThat("$.sentTime", is(notNullValue()))
        ;

        final Optional<String> publicSentEvent = getEventByNotificationId(publicNotificationSentConsumerClient, cppNotificationId);

        if (publicSentEvent.isPresent()) {
            final JsonPath jsonPathPublicSentEvent = new JsonPath(publicSentEvent.get());
            assertThat(jsonPathPublicSentEvent.get("notificationId"), is(cppNotificationId.toString()));
            assertThat(jsonPathPublicSentEvent.get("sentTime"), notNullValue());
        } else {
            fail();
        }
    }


    @Test
    public void shouldRetryFourTimesForLetterAcceptedStatus() throws IOException {

        final UUID cppNotificationId = randomUUID();

        final UUID govNotifyNotificationId = randomUUID();
        final String filePath = "pdf/JohnBloggs.pdf";

        stubDocumentDownload(getFileContent(filePath));
        stubGovNotifySendLetterWithStatusDelivered(cppNotificationId, govNotifyNotificationId, getBase64EncodedFileContent(PDF_FILE_PATH));
        stubGovNotifyForPendingVirusCheck(cppNotificationId, govNotifyNotificationId);

        final String payload = createObjectBuilder()
                .add("letterUrl", DOCUMENT_DOWNLOAD_URL)
                .add("clientContext", CLIENT_CONTEXT)
                .build().toString();

        final Response response = sendLetterNotificationJson(cppNotificationId.toString(),
                SEND_LETTER_POST_CONTENT_TYPE, payload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final Optional<String> publicFailedEvent = getEventByNotificationId(publicNotificationFailedConsumerClient, cppNotificationId);

        if (publicFailedEvent.isPresent()) {

            final String json = publicFailedEvent.get();

            with(json)
                    .assertThat("$._metadata.name", is("public.notificationnotify.events.notification-failed"))
                    .assertThat("notificationId", is(cppNotificationId.toString()))
                    .assertThat("errorMessage", is("Failed to send notification. Check delivery status failed after 4 attempts. Permanent failure. Gov.Notify responded with status 'pending-virus-check'"))
                    .assertThat("failedTime", is(notNullValue()))
                    .assertThat("clientContext", is(CLIENT_CONTEXT))

            ;

        } else {
            fail();
        }
    }

    @Test
    public void shouldRetryFiveTimesForLetterReceivedStatus() throws IOException {

        final UUID cppNotificationId = randomUUID();

        final UUID govNotifyNotificationId = randomUUID();

        stubDocumentDownload(getFileContent(PDF_FILE_PATH));
        stubGovNotifySendLetterWithStatusDelivered(cppNotificationId, govNotifyNotificationId, getBase64EncodedFileContent(PDF_FILE_PATH));
        stubGovNotifyForLetterAccepted(cppNotificationId, govNotifyNotificationId);

        final String payload = createObjectBuilder()
                .add("letterUrl", DOCUMENT_DOWNLOAD_URL)
                .build().toString();

        final Response response = sendLetterNotificationJson(cppNotificationId.toString(),
                SEND_LETTER_POST_CONTENT_TYPE, payload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final Optional<String> publicFailedEvent = getEventByNotificationId(publicNotificationFailedConsumerClient, cppNotificationId);

        if (publicFailedEvent.isPresent()) {

            final String json = publicFailedEvent.get();

            with(json)
                    .assertThat("$._metadata.name", is("public.notificationnotify.events.notification-failed"))
                    .assertThat("notificationId", is(cppNotificationId.toString()))
                    .assertThat("errorMessage", is("Failed to send notification. Check delivery status failed after 5 attempts. Permanent failure. Gov.Notify responded with status 'accepted'"))
                    .assertThat("failedTime", is(notNullValue()))
            ;

        } else {
            fail();
        }
    }


    @Test
    public void shouldFailToSendLetterDueToPermanentGovNotifyFailureResponse() throws IOException {
        final UUID cppNotificationId = randomUUID();

        stubDocumentDownload(getFileContent(PDF_FILE_PATH));
        stubGovNotifyPermanentFailureForSendLetter(cppNotificationId, getBase64EncodedFileContent(PDF_FILE_PATH));

        final String payload = createObjectBuilder()
                .add("letterUrl", DOCUMENT_DOWNLOAD_URL)
                .build().toString();
        final Response response = sendLetterNotificationJson(cppNotificationId.toString(), SEND_LETTER_POST_CONTENT_TYPE, payload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final int expectedNumberOfEvents = 3;
        final List<Event> events = eventFetcher.getEventsByStreamAndSize(cppNotificationId, expectedNumberOfEvents);

        assertThat(events.size(), is(expectedNumberOfEvents));

        final List<String> eventNames = events.stream()
                .map(Event::getName)
                .collect(toList());

        assertThat(eventNames.size(), is(expectedNumberOfEvents));

        assertThat(eventNames, hasItems(
                "notificationnotify.events.letter-queued",
                "notificationnotify.events.notification-attempted",
                "notificationnotify.events.notification-failed"

        ));

        final Optional<String> publicFailedEvent = getEventByNotificationId(publicNotificationFailedConsumerClient, cppNotificationId);

        if (publicFailedEvent.isPresent()) {
            final JsonPath jsonPathPublicFailedEvent = new JsonPath(publicFailedEvent.get());
            assertThat(jsonPathPublicFailedEvent.get("notificationId"), is(cppNotificationId.toString()));
            assertThat(jsonPathPublicFailedEvent.get("failedTime"), notNullValue());
        } else {
            fail();
        }
    }

    @Test
    public void shouldFailToSendLetterDueToTemporaryGovNotifyFailureResponse() throws IOException {
        final UUID cppNotificationId = randomUUID();

        stubDocumentDownload(getFileContent(PDF_FILE_PATH));
        stubGovNotifyTemporaryFailureForSendLetter(cppNotificationId, getBase64EncodedFileContent(PDF_FILE_PATH));

        final String payload = createObjectBuilder()
                .add("letterUrl", DOCUMENT_DOWNLOAD_URL)
                .build().toString();
        final Response response = sendLetterNotificationJson(cppNotificationId.toString(), SEND_LETTER_POST_CONTENT_TYPE, payload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));


        final List<Event> events = eventFetcher.getEventsByStreamAndSize(cppNotificationId, 6);

        final List<String> eventNames = events.stream()
                .map(Event::getName)
                .collect(toList());

        assertThat(eventNames.size(), is(6));
        assertThat(eventNames, hasItems(
                "notificationnotify.events.letter-queued",
                "notificationnotify.events.notification-attempted",
                "notificationnotify.events.notification-attempted",
                "notificationnotify.events.notification-attempted",
                "notificationnotify.events.notification-attempted",
                "notificationnotify.events.notification-failed"));

        final Optional<String> publicFailedEvent = getEventByNotificationId(publicNotificationFailedConsumerClient, cppNotificationId);

        if (publicFailedEvent.isPresent()) {
            final JsonPath jsonPathPublicFailedEvent = new JsonPath(publicFailedEvent.get());
            assertThat(jsonPathPublicFailedEvent.get("notificationId"), is(cppNotificationId.toString()));
            assertThat(jsonPathPublicFailedEvent.get("failedTime"), notNullValue());
        } else {
            fail();
        }
    }

    @Test
    public void shouldFailToSendLetterAsUnavailablePdf() {
        final UUID cppNotificationId = randomUUID();
        stubDocumentDownloadFailure();

        final String payload = createObjectBuilder()
                .add("letterUrl", DOCUMENT_DOWNLOAD_URL)
                .build().toString();
        final Response response = sendLetterNotificationJson(cppNotificationId.toString(), SEND_LETTER_POST_CONTENT_TYPE, payload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final List<Event> events = eventFetcher.getEventsByStreamAndSize(cppNotificationId, 6);
        final List<String> eventNames = events.stream()
                .map(Event::getName)
                .collect(toList());

        assertThat(eventNames.size(), is(6));
        assertThat(eventNames, hasItems(
                "notificationnotify.events.letter-queued",
                "notificationnotify.events.notification-attempted",
                "notificationnotify.events.notification-attempted",
                "notificationnotify.events.notification-attempted",
                "notificationnotify.events.notification-attempted",
                "notificationnotify.events.notification-failed"));

        final Optional<String> publicFailedEvent = getEventByNotificationId(publicNotificationFailedConsumerClient, cppNotificationId);

        if (publicFailedEvent.isPresent()) {
            final JsonPath jsonPathPublicFailedEvent = new JsonPath(publicFailedEvent.get());
            assertThat(jsonPathPublicFailedEvent.get("notificationId"), is(cppNotificationId.toString()));
            assertThat(jsonPathPublicFailedEvent.get("failedTime"), notNullValue());
        } else {
            fail();
        }
    }

    @Test
    public void shouldFailToSendLetterDueToVirusScanGovNotifyFailureResponse() throws IOException {
        final UUID cppNotificationId = randomUUID();
        final UUID govNotifyNotificationId = randomUUID();

        stubDocumentDownload(getFileContent(PDF_FILE_PATH));
        stubGovNotifySendLetterWithStatusDelivered(cppNotificationId, govNotifyNotificationId, getBase64EncodedFileContent(PDF_FILE_PATH));
        stubGovNotifyPermanentFailureForVirusScannedFailed(cppNotificationId, getBase64EncodedFileContent(PDF_FILE_PATH));

        final String payload = createObjectBuilder()
                .add("letterUrl", DOCUMENT_DOWNLOAD_URL)
                .build().toString();
        final Response response = sendLetterNotificationJson(cppNotificationId.toString(), SEND_LETTER_POST_CONTENT_TYPE, payload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final List<Event> events = eventFetcher.getEventsByStreamAndSize(cppNotificationId, 3);
        final List<String> eventNames = events.stream()
                .map(Event::getName)
                .collect(toList());

        assertThat(eventNames.size(), is(3));
        assertThat(eventNames, hasItems(
                "notificationnotify.events.letter-queued",
                "notificationnotify.events.notification-attempted",
                "notificationnotify.events.notification-failed"
        ));

        final Optional<String> publicFailedEvent = getEventByNotificationId(publicNotificationFailedConsumerClient, cppNotificationId);

        if (publicFailedEvent.isPresent()) {
            final JsonPath jsonPathPublicFailedEvent = new JsonPath(publicFailedEvent.get());
            assertThat(jsonPathPublicFailedEvent.get("notificationId"), is(cppNotificationId.toString()));
            assertThat(jsonPathPublicFailedEvent.get("failedTime"), notNullValue());
        } else {
            fail();
        }
    }


    @Test
    public void shouldPermanentlyFailForValidationError() throws IOException {

        final UUID cppNotificationId = randomUUID();

        final UUID govNotifyNotificationId = randomUUID();

        stubDocumentDownload(getFileContent(PDF_FILE_PATH));
        stubGovNotifySendLetterWithStatusDelivered(cppNotificationId, govNotifyNotificationId, getBase64EncodedFileContent(PDF_FILE_PATH));
        stubGovNotifyCheckStatusWithStatusValidationError(cppNotificationId, govNotifyNotificationId);

        final String payload = createObjectBuilder()
                .add("letterUrl", DOCUMENT_DOWNLOAD_URL)
                .build().toString();

        final Response response = sendLetterNotificationJson(cppNotificationId.toString(),
                SEND_LETTER_POST_CONTENT_TYPE, payload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final int expectedNumberOfEvents = 7;
        final List<Event> events = eventFetcher.getEventsByStreamAndSize(cppNotificationId, expectedNumberOfEvents);

        assertThat(events.size(), is(expectedNumberOfEvents));

        final List<String> eventNames = events.stream()
                .map(Event::getName)
                .collect(toList());

        assertThat(eventNames.size(), is(expectedNumberOfEvents));

        assertThat(eventNames, hasItems(
                "notificationnotify.events.letter-queued",
                "notificationnotify.events.letter-queued-for-resend",
                "notificationnotify.events.letter-queued-for-resend",
                "notificationnotify.events.letter-queued-for-resend",
                "notificationnotify.events.letter-queued-for-resend",
                "notificationnotify.events.letter-queued-for-resend",
                "notificationnotify.events.notification-failed"

        ));

        final Optional<String> publicFailedEvent = getEventByNotificationId(publicNotificationFailedConsumerClient, cppNotificationId);

        if (publicFailedEvent.isPresent()) {

            final String json = publicFailedEvent.get();

            final String substring = format("Validation failed for '%s' automatic resend attempt, remaining = 0", cppNotificationId);
            with(json)
                    .assertThat("$._metadata.name", is("public.notificationnotify.events.notification-failed"))
                    .assertThat("notificationId", is(cppNotificationId.toString()))
                    .assertThat("errorMessage", containsString(substring))
                    .assertThat("failedTime", is(notNullValue()));
        } else {
            fail();
        }
    }


    @Test
    public void shouldRetryForUnexpectedStatusDuringCheckStatusAndPermanentlyFail() throws IOException {
        final UUID cppNotificationId = randomUUID();
        final UUID govNotifyNotificationId = randomUUID();

        stubDocumentDownload(getFileContent(PDF_FILE_PATH));
        stubGovNotifySendLetterWithStatusDelivered(cppNotificationId, govNotifyNotificationId, getBase64EncodedFileContent(PDF_FILE_PATH));
        stubGovNotifyCheckStatusWithStatusUnexpected(cppNotificationId, govNotifyNotificationId);

        final String payload = createObjectBuilder()
                .add("letterUrl", DOCUMENT_DOWNLOAD_URL)
                .build().toString();
        final Response response = sendLetterNotificationJson(cppNotificationId.toString(), SEND_LETTER_POST_CONTENT_TYPE, payload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final List<Event> events = eventFetcher.getEventsByStreamAndSize(cppNotificationId, 2);

        final List<String> eventNames = events.stream()
                .map(Event::getName)
                .collect(toList());

        assertThat(eventNames.size(), is(2));
        assertThat(eventNames, hasItems(
                "notificationnotify.events.letter-queued",
                "notificationnotify.events.notification-failed"));

        final Optional<String> publicFailedEvent = getEventByNotificationId(publicNotificationFailedConsumerClient, cppNotificationId);

        if (publicFailedEvent.isPresent()) {

            final String json = publicFailedEvent.get();

            with(json)
                    .assertThat("$._metadata.name", is("public.notificationnotify.events.notification-failed"))
                    .assertThat("notificationId", is(cppNotificationId.toString()))
                    .assertThat("errorMessage", is("Failed to send notification. Check delivery status failed after 4 attempts. Permanent failure. Gov.Notify responded with status 'unexpected'"))
                    .assertThat("failedTime", is(notNullValue()));

        } else {
            fail();
        }
    }

    private Response sendLetterNotificationJson(final String resource,
                                                final String mediaType,
                                                final String payload) {
        final MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add(USER_ID, NOTIFY_SYSTEM_USER);
        return restClient.postCommand(SEND_NOTIFICATION_POST_URL + resource, mediaType, payload, headers);
    }


    private Optional<String> getEventByNotificationId(final JmsMessageConsumerClient messageConsumerClient,
                                                      final UUID notificationId) {

        return poller.pollUntilFound(() -> {
            final Optional<String> eventOptional = messageConsumerClient.retrieveMessage();
            if (eventOptional.isPresent()) {
                final String eventJson = eventOptional.get();

                final String foundNotificationId = new JsonPath(eventJson).getString("notificationId");

                if (foundNotificationId.equals(notificationId.toString())) {
                    return eventOptional;
                }
            }

            return empty();
        });
    }
}
