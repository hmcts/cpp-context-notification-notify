package uk.gov.moj.notification.notify.it;

import static com.jayway.jsonassert.JsonAssert.with;
import static java.lang.String.format;
import static java.util.Optional.empty;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static org.hamcrest.CoreMatchers.hasItems;
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
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyCheckStatusWithStatusDelivered;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyPermanentFailureForSendEmail;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyPermanentFailureForSendEmailWithMaterialLink;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifySuccessClientWithPersonalisation;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifySuccessClientWithPersonalisationContainingCsvFile;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifySuccessClientWithPersonalisationWithoutMaterialUrl;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifySuccessClientWithoutPersonalisation;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyTemporaryFailureForSendEmail;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubOffice365NotifySuccessClientWithPersonalisation;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubUsersGroups;
import static uk.gov.moj.notification.notify.it.util.PayloadGeneratorUtil.payloadWithPersonalisationWithFileId;
import static uk.gov.moj.notification.notify.it.util.PayloadGeneratorUtil.payloadWithPersonalisationWithMaterialUrl;
import static uk.gov.moj.notification.notify.it.util.PayloadGeneratorUtil.payloadWithPersonalisationWithoutMaterialUrl;
import static uk.gov.moj.notification.notify.it.util.PayloadGeneratorUtil.payloadWithoutPersonalisation;
import static uk.gov.moj.notification.notify.it.util.PayloadGeneratorUtil.payloadWithoutPersonalisationWithFileId;
import static uk.gov.moj.notification.notify.it.util.ResourceLoader.getBase64EncodedFileContent;
import static uk.gov.moj.notification.notify.it.util.ResourceLoader.getFileContent;
import static uk.gov.moj.notification.notify.it.util.ResourceLoader.getFileContentForLargeFile;

import uk.gov.justice.services.eventsourcing.repository.jdbc.event.Event;
import uk.gov.justice.services.fileservice.api.FileServiceException;
import uk.gov.justice.services.integrationtest.utils.jms.JmsMessageConsumerClient;
import uk.gov.justice.services.test.utils.core.messaging.Poller;
import uk.gov.justice.services.test.utils.core.rest.RestClient;
import uk.gov.moj.notification.notify.it.util.EventFetcher;
import uk.gov.moj.notification.notify.it.util.FileServiceHelper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;

import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class NotifyEmailIT extends BaseIT {

    private static final String PORT = "8080";
    private static final String NOTIFY_SYSTEM_USER = randomUUID().toString();
    private static final String SEND_NOTIFICATION_POST_URL = format("http://%s:%s/notificationnotify-command-api/command/api/rest/notificationnotify/notifications/", getHost(), PORT);
    private static final String SEND_EMAIL_POST_CONTENT_TYPE = "application/vnd.notificationnotify.email+json";

    private final RestClient restClient = new RestClient();
    private EventFetcher eventFetcher = null;

    private static final String PUBLIC_EVENT_SENT = "public.notificationnotify.events.notification-sent";
    private static final String PUBLIC_EVENT_FAILED = "public.notificationnotify.events.notification-failed";

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
    public void shouldSendSuccessfulEmailWithPersonalisationWithoutMaterialUrl() {
        final UUID cppNotificationId = randomUUID();
        final UUID govNotifyNotificationId = randomUUID();
        stubGovNotifySuccessClientWithPersonalisationWithoutMaterialUrl(cppNotificationId, govNotifyNotificationId);
        stubGovNotifyCheckStatusWithStatusDelivered(cppNotificationId, govNotifyNotificationId);

        final Response response = sendEmailNotificationJson(cppNotificationId.toString(), SEND_EMAIL_POST_CONTENT_TYPE, payloadWithPersonalisationWithoutMaterialUrl());

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final int expectedNumberOfEvents = 2;
        final List<Event> events = eventFetcher.getEventsByStreamAndSize(cppNotificationId, expectedNumberOfEvents);

        assertThat(events.size(), is(expectedNumberOfEvents));

        final Event queuedEvent = events.get(0);
        assertThat(queuedEvent.getName(), is("notificationnotify.events.notification-queued"));
        with(queuedEvent.getMetadata())
                .assertThat("stream.id", is(cppNotificationId.toString()))
                .assertThat("stream.version", is(1));
        with(queuedEvent.getPayload())
                .assertThat("$.notificationId", is(cppNotificationId.toString()))
                .assertThat("$.personalisation", is(notNullValue()))
                .assertThat("$.replyToAddress", is("the.grand.inquisitor@moj.gov.uk"))
                .assertThat("$.sendToAddress", is("fred.bloggs@acme.com"));;


        final Event sentEvent = events.get(1);
        assertThat(sentEvent.getName(), is("notificationnotify.events.notification-sent"));
        with(sentEvent.getMetadata())
                .assertThat("stream.id", is(cppNotificationId.toString()))
                .assertThat("stream.version", is(2));

        with(sentEvent.getPayload())
                .assertThat("$.notificationId", is(cppNotificationId.toString()))
                .assertThat("$.sentTime", is(notNullValue()))
                .assertThat("clientContext", is(CLIENT_CONTEXT))
                .assertThat("$.sendToAddress", is("fred.bloggs@acme.com"))
                .assertThat("$.emailBody", is("Email Body"))
                .assertThat("$.emailSubject", is("Email Subject"));

        verifyPublicSentEvent(publicNotificationSentConsumerClient, cppNotificationId);
    }

    @Test
    public void shouldFailToSendEmailAsUnavailablePdf() {
        final UUID cppNotificationId = randomUUID();
        stubDocumentDownloadFailure();
        stubGovNotifyPermanentFailureForSendEmail(cppNotificationId);

        final Response response = sendEmailNotificationJson(cppNotificationId.toString(), SEND_EMAIL_POST_CONTENT_TYPE, payloadWithPersonalisationWithMaterialUrl(DOCUMENT_DOWNLOAD_URL));

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final List<Event> events = eventFetcher.getEventsByStreamAndSize(cppNotificationId, 5);
        final List<String> eventNames = events.stream()
                .map(Event::getName)
                .collect(toList());

        assertThat(eventNames.size(), is(5));
        assertThat(eventNames, hasItems(
                "notificationnotify.events.notification-queued",
                "notificationnotify.events.notification-attempted",
                "notificationnotify.events.notification-attempted",
                "notificationnotify.events.notification-attempted",
                "notificationnotify.events.notification-failed"));

        final Optional<String> publicFailedEvent = getEventByNotificationId(publicNotificationFailedConsumerClient, cppNotificationId);

        if (publicFailedEvent.isPresent()) {
            final JsonPath jsonPathPublicFailedEvent = new JsonPath(publicFailedEvent.get());
            assertThat(jsonPathPublicFailedEvent.get("notificationId"), is(cppNotificationId.toString()));
            assertThat(jsonPathPublicFailedEvent.get("failedTime"), notNullValue());
            assertThat(jsonPathPublicFailedEvent.get("clientContext"), is(CLIENT_CONTEXT));
        } else {
            fail();
        }
    }

    @Test
    public void shouldFailToSendEmailAsUnavailablePdfInFileService() {
        final UUID cppNotificationId = randomUUID();
        final UUID fileId = randomUUID();
        stubDocumentDownloadFailure();
        stubGovNotifyPermanentFailureForSendEmail(cppNotificationId);

        final Response response = sendEmailNotificationJson(cppNotificationId.toString(), SEND_EMAIL_POST_CONTENT_TYPE, payloadWithPersonalisationWithFileId(fileId));

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final List<Event> events = eventFetcher.getEventsByStreamAndSize(cppNotificationId, 5);
        final List<String> eventNames = events.stream()
                .map(Event::getName)
                .collect(toList());

        assertThat(eventNames.size(), is(5));
        assertThat(eventNames, hasItems(
                "notificationnotify.events.notification-queued",
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
    @Disabled
    @SuppressWarnings("squid:S1607")
    //Will fix after this merge need to do complex wiremocking for dynamic URL
    public void shouldSendSuccessfulEmailWithPersonalisationWithEmailUrlWithFileLessThanMaxLimit() throws IOException {
        final UUID cppNotificationId = randomUUID();
        final UUID govNotifyNotificationId = randomUUID();

        final String materialUrl = "http://localhost:8080/somewhere/letter/90fe9fe3-0e03-40c3-8298-ec6f55323582";
        final String filePath = "pdf/JohnBloggs.pdf";
        stubDocumentDownload(getFileContent(filePath));

        stubGovNotifyCheckStatusWithStatusDelivered(cppNotificationId, govNotifyNotificationId);

        final Response response = sendEmailNotificationJson(cppNotificationId.toString(), SEND_EMAIL_POST_CONTENT_TYPE, payloadWithPersonalisationWithMaterialUrl(materialUrl));

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final int expectedNumberOfEvents = 2;
        final List<Event> events = eventFetcher.getEventsByStreamAndSize(cppNotificationId, expectedNumberOfEvents);

        assertThat(events.size(), is(expectedNumberOfEvents));

        final Event queuedEvent = events.get(0);
        assertThat(queuedEvent.getName(), is("notificationnotify.events.notification-queued"));
        with(queuedEvent.getMetadata())
                .assertThat("stream.id", is(cppNotificationId.toString()))
                .assertThat("stream.version", is(1));
        with(queuedEvent.getPayload())
                .assertThat("$.notificationId", is(cppNotificationId.toString()))
                .assertThat("$.personalisation", is(notNullValue()))
                .assertThat("$.replyToAddress", is("the.grand.inquisitor@moj.gov.uk"))
                .assertThat("$.sendToAddress", is("fred.bloggs@acme.com"));;


        final Event sentEvent = events.get(1);
        assertThat(sentEvent.getName(), is("notificationnotify.events.notification-sent"));
        with(sentEvent.getMetadata())
                .assertThat("stream.id", is(cppNotificationId.toString()))
                .assertThat("stream.version", is(2));
        with(sentEvent.getPayload())
                .assertThat("$.notificationId", is(cppNotificationId.toString()))
                .assertThat("$.sentTime", is(notNullValue()))
                .assertThat("clientContext", is(CLIENT_CONTEXT))
                .assertThat("$.replyToAddress", is("the.grand.inquisitor@moj.gov.uk"))
                .assertThat("$.sendToAddress", is("fred.bloggs@acme.com"))
                .assertThat("$.emailBody", is("Email Body"))
                .assertThat("$.emailSubject", is("Email Subject"));


        verifyPublicSentEvent(publicNotificationSentConsumerClient, cppNotificationId);
    }

    @Test
    @Disabled
    @SuppressWarnings("squid:S1607")
    //Will fix later  for external api stubbing as it is calling the actual api.
    public void shouldSendSuccessfulEmailWithPersonalisationWithEmailUrlWithFileSizeBetween2to15MB() throws IOException {
        final UUID cppNotificationId = randomUUID();

        final String materialUrl = "http://localhost:8080/somewhere/letter/90fe9fe3-0e03-40c3-8298-ec6f55323582";
        final String filePath = "pdf/LargeSampleBetween2to15MB.pdf";

        stubDocumentDownload(getFileContent(filePath));

        stubOffice365NotifySuccessClientWithPersonalisation(cppNotificationId, materialUrl);


        final Response response = sendEmailNotificationJson(cppNotificationId.toString(), SEND_EMAIL_POST_CONTENT_TYPE, payloadWithPersonalisationWithMaterialUrl(materialUrl));

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final int expectedNumberOfEvents = 2;
        final List<Event> events = eventFetcher.getEventsByStreamAndSize(cppNotificationId, expectedNumberOfEvents);

        assertThat(events.size(), is(expectedNumberOfEvents));

        final Event queuedEvent = events.get(0);
        assertThat(queuedEvent.getName(), is("notificationnotify.events.notification-queued"));
        with(queuedEvent.getMetadata())
                .assertThat("stream.id", is(cppNotificationId.toString()))
                .assertThat("stream.version", is(1));
        with(queuedEvent.getPayload())
                .assertThat("$.notificationId", is(cppNotificationId.toString()))
                .assertThat("$.personalisation", is(notNullValue()))
                .assertThat("$.replyToAddress", is("the.grand.inquisitor@moj.gov.uk"))
                .assertThat("$.sendToAddress", is("fred.bloggs@acme.com"));;


        final Event sentEvent = events.get(1);
        assertThat(sentEvent.getName(), is("notificationnotify.events.notification-sent"));
        with(sentEvent.getMetadata())
                .assertThat("stream.id", is(cppNotificationId.toString()))
                .assertThat("stream.version", is(2));
        with(sentEvent.getPayload())
                .assertThat("$.notificationId", is(cppNotificationId.toString()))
                .assertThat("$.sentTime", is(notNullValue()))
                .assertThat("clientContext", is(CLIENT_CONTEXT))
                .assertThat("$.replyToAddress", is("the.grand.inquisitor@moj.gov.uk"))
                .assertThat("$.sendToAddress", is("fred.bloggs@acme.com"))
                .assertThat("$.emailBody", is("Email Body"))
                .assertThat("$.emailSubject", is("Email Subject"));


        verifyPublicSentEvent(publicNotificationSentConsumerClient, cppNotificationId);
    }

    @Test
    @SuppressWarnings("squid:S1607")
    public void shouldSendSuccessfulEmailWithPersonalisationWithFileIdWithFileLessThanMaxLimit() throws IOException, SQLException, FileServiceException {
        final UUID cppNotificationId = randomUUID();
        final UUID govNotifyNotificationId = randomUUID();
        final String fileName = "shouldSendSuccessfulEmailWithPersonalisationWithFileIdWithFileLessThanMaxLimit.txt";
        final String mediaType = "application/text";
        final String content = "content";
        final InputStream contentStream = new ByteArrayInputStream(content.getBytes());
        final UUID fileId = FileServiceHelper.create(fileName, mediaType, contentStream);
        contentStream.close();
        stubGovNotifySuccessClientWithPersonalisation(cppNotificationId, govNotifyNotificationId, content, false);
        stubGovNotifyCheckStatusWithStatusDelivered(cppNotificationId, govNotifyNotificationId);

        final Response response = sendEmailNotificationJson(cppNotificationId.toString(), SEND_EMAIL_POST_CONTENT_TYPE, payloadWithPersonalisationWithFileId(fileId));

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final int expectedNumberOfEvents = 2;
        final List<Event> events = eventFetcher.getEventsByStreamAndSize(cppNotificationId, expectedNumberOfEvents);

        assertThat(events.size(), is(expectedNumberOfEvents));

        final Event queuedEvent = events.get(0);
        assertThat(queuedEvent.getName(), is("notificationnotify.events.notification-queued"));
        with(queuedEvent.getMetadata())
                .assertThat("stream.id", is(cppNotificationId.toString()))
                .assertThat("stream.version", is(1));
        with(queuedEvent.getPayload())
                .assertThat("$.notificationId", is(cppNotificationId.toString()))
                .assertThat("$.personalisation", is(notNullValue()))
                .assertThat("$.replyToAddress", is("the.grand.inquisitor@moj.gov.uk"))
                .assertThat("$.sendToAddress", is("fred.bloggs@acme.com"));;


        final Event sentEvent = events.get(1);
        assertThat(sentEvent.getName(), is("notificationnotify.events.notification-sent"));
        with(sentEvent.getMetadata())
                .assertThat("stream.id", is(cppNotificationId.toString()))
                .assertThat("stream.version", is(2));
        with(sentEvent.getPayload())
                .assertThat("$.notificationId", is(cppNotificationId.toString()))
                .assertThat("$.sentTime", is(notNullValue()))
                .assertThat("clientContext", is(CLIENT_CONTEXT))
                .assertThat("$.sendToAddress", is("fred.bloggs@acme.com"))
                .assertThat("$.emailBody", is("Email Body"))
                .assertThat("$.emailSubject", is("Email Subject"));


        verifyPublicSentEvent(publicNotificationSentConsumerClient, cppNotificationId);
    }

    @Test
    @SuppressWarnings("squid:S1607")
    public void shouldSendSuccessfulEmailWithCsvFileSizeLessThan2MB() throws IOException, SQLException, FileServiceException {
        final UUID cppNotificationId = randomUUID();
        final UUID govNotifyNotificationId = randomUUID();
        final String filePath = "csv/NCES_DATA_200702.csv";
        final String mediaType = "application/text";
        final InputStream contentStream = new ByteArrayInputStream(getFileContent(filePath));
        final UUID fileId = FileServiceHelper.create(filePath, mediaType, contentStream);
        contentStream.close();

        stubGovNotifySuccessClientWithPersonalisationContainingCsvFile(cppNotificationId, govNotifyNotificationId, getBase64EncodedFileContent(filePath), true);
        stubGovNotifyCheckStatusWithStatusDelivered(cppNotificationId, govNotifyNotificationId);

        final Response response = sendEmailNotificationJson(cppNotificationId.toString(), SEND_EMAIL_POST_CONTENT_TYPE, payloadWithoutPersonalisationWithFileId(fileId));

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final int expectedNumberOfEvents = 2;
        final List<Event> events = eventFetcher.getEventsByStreamAndSize(cppNotificationId, expectedNumberOfEvents);

        assertThat(events.size(), is(expectedNumberOfEvents));

        final Event queuedEvent = events.get(0);
        assertThat(queuedEvent.getName(), is("notificationnotify.events.notification-queued"));
        with(queuedEvent.getMetadata())
                .assertThat("stream.id", is(cppNotificationId.toString()))
                .assertThat("stream.version", is(1));
        with(queuedEvent.getPayload())
                .assertThat("$.notificationId", is(cppNotificationId.toString()))
                .assertThat("$.fileId", is(fileId.toString()))
                .assertThat("$.replyToAddress", is("the.grand.inquisitor@moj.gov.uk"))
                .assertThat("$.sendToAddress", is("fred.bloggs@acme.com"));

        final Event sentEvent = events.get(1);
        assertThat(sentEvent.getName(), is("notificationnotify.events.notification-sent"));
        with(sentEvent.getMetadata())
                .assertThat("stream.id", is(cppNotificationId.toString()))
                .assertThat("stream.version", is(2));
        with(sentEvent.getPayload())
                .assertThat("$.notificationId", is(cppNotificationId.toString()))
                .assertThat("$.sentTime", is(notNullValue()))
                .assertThat("clientContext", is(CLIENT_CONTEXT))
                .assertThat("$.sendToAddress", is("fred.bloggs@acme.com"))
                .assertThat("$.emailBody", is("Email Body"))
                .assertThat("$.emailSubject", is("Email Subject"));

        verifyPublicSentEvent(publicNotificationSentConsumerClient, cppNotificationId);
    }

    @Test
    @Disabled
    @SuppressWarnings("squid:S1607")
    //Will fix later  for large file size stubbing
    public void shouldFailSendingEmailWithPersonalisationWithEmailUrlWithFileGreaterThanMaxLimit() throws IOException {
        final UUID cppNotificationId = randomUUID();
        final String materialLink = "http://localhost:8080/somewhere/letter/90fe9fe3-0e03-40c3-8298-ec6f55323582";
        final String filePath = "pdf/LargeSampleBiggerThan15MB.pdf";
        stubDocumentDownload(getFileContentForLargeFile(filePath));

        stubGovNotifyPermanentFailureForSendEmailWithMaterialLink(cppNotificationId, materialLink);

        final Response response = sendEmailNotificationJson(cppNotificationId.toString(), SEND_EMAIL_POST_CONTENT_TYPE, payloadWithPersonalisationWithMaterialUrl(materialLink));

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final int expectedNumberOfEvents = 3;
        final List<Event> events = eventFetcher.getEventsByStreamAndSize(cppNotificationId, expectedNumberOfEvents);

        assertThat(events.size(), is(expectedNumberOfEvents));

        final List<String> eventNames = events.stream()
                .map(Event::getName)
                .collect(toList());

        assertThat(eventNames.size(), is(expectedNumberOfEvents));

        assertThat(eventNames, hasItems(
                "notificationnotify.events.notification-queued",
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
    public void shouldSendSuccessfulEmailWithoutPersonalisation() {
        final UUID cppNotificationId = randomUUID();
        final UUID govNotifyNotificationId = randomUUID();
        stubGovNotifySuccessClientWithoutPersonalisation(cppNotificationId, govNotifyNotificationId);
        stubGovNotifyCheckStatusWithStatusDelivered(cppNotificationId, govNotifyNotificationId);

        final Response response = sendEmailNotificationJson(cppNotificationId.toString(), SEND_EMAIL_POST_CONTENT_TYPE, payloadWithoutPersonalisation());

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final int expectedNumberOfEvents = 2;
        final List<Event> events = eventFetcher.getEventsByStreamAndSize(cppNotificationId, expectedNumberOfEvents);

        assertThat(events.size(), is(expectedNumberOfEvents));

        final Event queuedEvent = events.get(0);
        assertThat(queuedEvent.getName(), is("notificationnotify.events.notification-queued"));
        with(queuedEvent.getMetadata())
                .assertThat("stream.id", is(cppNotificationId.toString()))
                .assertThat("stream.version", is(1));
        with(queuedEvent.getPayload())
                .assertThat("$.notificationId", is(cppNotificationId.toString()))
                .assertThat("$.sendToAddress", is("fred.bloggs@acme.com"))
                .assertThat("$.replyToAddress", is("the.grand.inquisitor@moj.gov.uk"))
                .assertThat("$.templateId", is(notNullValue()));

        final Event sentEvent = events.get(1);
        assertThat(sentEvent.getName(), is("notificationnotify.events.notification-sent"));
        with(sentEvent.getMetadata())
                .assertThat("stream.id", is(cppNotificationId.toString()))
                .assertThat("stream.version", is(2));
        with(sentEvent.getPayload())
                .assertThat("$.notificationId", is(cppNotificationId.toString()))
                .assertThat("$.sentTime", is(notNullValue()))
                .assertThat("$.sendToAddress", is("fred.bloggs@acme.com"))
                .assertThat("$.emailBody", is("Email Body"))
                .assertThat("$.emailSubject", is("Email Subject"));

        verifyPublicSentEvent(publicNotificationSentConsumerClient, cppNotificationId);
    }

    @Test
    public void shouldFailToSendEmailDueToPermanentGovNotifyFailureResponse() {
        final UUID cppNotificationId = randomUUID();
        stubGovNotifyPermanentFailureForSendEmail(cppNotificationId);

        final Response response = sendEmailNotificationJson(cppNotificationId.toString(), SEND_EMAIL_POST_CONTENT_TYPE, payloadWithPersonalisationWithoutMaterialUrl());

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final int expectedNumberOfEvents = 3;
        final List<Event> events = eventFetcher.getEventsByStreamAndSize(cppNotificationId, expectedNumberOfEvents);

        assertThat(events.size(), is(expectedNumberOfEvents));

        final List<String> eventNames = events.stream()
                .map(Event::getName)
                .collect(toList());

        assertThat(eventNames, hasItems(
                "notificationnotify.events.notification-queued",
                "notificationnotify.events.notification-attempted",
                "notificationnotify.events.notification-failed"
        ));

        verifyPublicFailedEvent(publicNotificationFailedConsumerClient, cppNotificationId);
    }

    @Test
    public void shouldFailToSendEmailDueToTemporaryGovNotifyFailureResponse() {
        final UUID cppNotificationId = randomUUID();
        stubGovNotifyTemporaryFailureForSendEmail(cppNotificationId);

        final Response response = sendEmailNotificationJson(cppNotificationId.toString(), SEND_EMAIL_POST_CONTENT_TYPE, payloadWithPersonalisationWithoutMaterialUrl());

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final int expectedNumberOfEvents = 5;
        final List<Event> events = eventFetcher.getEventsByStreamAndSize(cppNotificationId, expectedNumberOfEvents);

        assertThat(events.size(), is(expectedNumberOfEvents));

        final List<String> eventNames = events.stream()
                .map(Event::getName)
                .collect(toList());

        assertThat(eventNames, hasItems(
                "notificationnotify.events.notification-queued",
                "notificationnotify.events.notification-attempted",
                "notificationnotify.events.notification-attempted",
                "notificationnotify.events.notification-attempted",
                "notificationnotify.events.notification-failed"
        ));

        verifyPublicFailedEvent(publicNotificationFailedConsumerClient, cppNotificationId);
    }

    private Response sendEmailNotificationJson(final String resource,
                                               final String mediaType,
                                               final String payload) {
        final MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add(USER_ID, NOTIFY_SYSTEM_USER);
        return restClient.postCommand(SEND_NOTIFICATION_POST_URL + resource, mediaType, payload, headers);
    }

    public void verifyPublicSentEvent(final JmsMessageConsumerClient messageConsumerClient, final UUID notificationId) {
        final Optional<String> publicSentEvent = getEventByNotificationId(messageConsumerClient, notificationId);

        assertThat(publicSentEvent.isPresent(), is(true));

        final JsonPath jsonPathPublicSentEvent = new JsonPath(publicSentEvent.get());

        assertThat(jsonPathPublicSentEvent.get("notificationId"), is(notificationId.toString()));
        assertThat(jsonPathPublicSentEvent.get("sentTime"), notNullValue());
        assertThat(jsonPathPublicSentEvent.get("sendToAddress"), is("fred.bloggs@acme.com"));
        assertThat(jsonPathPublicSentEvent.get("emailBody"), is("Email Body"));
        assertThat(jsonPathPublicSentEvent.get("emailSubject"), is("Email Subject"));

    }

    public void verifyPublicFailedEvent(final JmsMessageConsumerClient messageConsumerClient, final UUID notificationId) {
        final Optional<String> publicFailedEvent = getEventByNotificationId(messageConsumerClient, notificationId);

        assertThat(publicFailedEvent.isPresent(), is(true));

        final JsonPath jsonPathPublicFailedEvent = new JsonPath(publicFailedEvent.get());

        assertThat(jsonPathPublicFailedEvent.get("notificationId"), is(notificationId.toString()));
        assertThat(jsonPathPublicFailedEvent.get("failedTime"), notNullValue());
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
