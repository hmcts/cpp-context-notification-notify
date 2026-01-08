package uk.gov.moj.notification.notify.it;

import static com.jayway.jsonassert.JsonAssert.with;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.justice.services.common.http.HeaderConstants.USER_ID;
import static uk.gov.justice.services.test.utils.common.host.TestHostProvider.getHost;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubEnableAllCapabilities;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyCheckStatusWhenPermanentFailure;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyCheckStatusWhenTemporaryFailure;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyCheckStatusWithStatusNotFound;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyCheckStatusWithStatusUnexpected;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifySuccessClientWithPersonalisation;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifySuccessClientWithPersonalisationWithoutMaterialUrl;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubUsersGroups;
import static uk.gov.moj.notification.notify.it.util.PayloadGeneratorUtil.payloadWithPersonalisationWithoutMaterialUrl;

import uk.gov.justice.services.eventsourcing.repository.jdbc.event.Event;
import uk.gov.justice.services.test.utils.core.rest.RestClient;
import uk.gov.moj.notification.notify.it.util.EventFetcher;

import java.util.List;
import java.util.UUID;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class NotifyCheckStatusIT extends BaseIT {

    private static final String PORT = "8080";
    private static final String NOTIFY_SYSTEM_USER = randomUUID().toString();
    private static final String SEND_EMAIL_POST_URL = format("http://%s:%s/notificationnotify-command-api/command/api/rest/notificationnotify/notifications/", getHost(), PORT);
    private static final String SEND_EMAIL_POST_CONTENT_TYPE = "application/vnd.notificationnotify.email+json";

    private final RestClient restClient = new RestClient();
    private EventFetcher eventFetcher = null;

    @BeforeEach
    public void setUp() throws Exception {
        stubEnableAllCapabilities();
        stubUsersGroups();
        eventFetcher = new EventFetcher();
    }

    @Test
    @Disabled("This test requires an enhanced stub to allow check status to return temporary failure and then success or permanent failure")
    public void shouldSendSuccessfulEmailButFailedWithTemporaryFailure() {
        final UUID cppNotificationId = randomUUID();
        final UUID govNotifyNotificationId = randomUUID();

        stubGovNotifySuccessClientWithPersonalisation(cppNotificationId, govNotifyNotificationId,"", false);
        stubGovNotifyCheckStatusWhenTemporaryFailure(cppNotificationId, govNotifyNotificationId);

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
        ;

        final Event failedEvent = events.get(1);
        assertThat(failedEvent.getName(), is("notificationnotify.events.notification-failed"));
        with(failedEvent.getMetadata())
                .assertThat("stream.id", is(cppNotificationId.toString()))
                .assertThat("stream.version", is(2));
        with(failedEvent.getPayload())
                .assertThat("$.notificationId", is(cppNotificationId.toString()))
                .assertThat("$.errorMessage", is("permanent-failure"))
                .assertThat("$.failedTime", is(notNullValue()))
        ;
    }

    @Test
    public void shouldSendSuccessfulEmailButFailedWithPermanentFailure() {
        final UUID cppNotificationId = randomUUID();
        final UUID govNotifyNotificationId = randomUUID();

        stubGovNotifySuccessClientWithPersonalisationWithoutMaterialUrl(cppNotificationId, govNotifyNotificationId);
        stubGovNotifyCheckStatusWhenPermanentFailure(cppNotificationId, govNotifyNotificationId);

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
        ;

        final Event failedEvent = events.get(1);
        assertThat(failedEvent.getName(), is("notificationnotify.events.notification-failed"));
        with(failedEvent.getMetadata())
                .assertThat("stream.id", is(cppNotificationId.toString()))
                .assertThat("stream.version", is(2));
        with(failedEvent.getPayload())
                .assertThat("$.notificationId", is(cppNotificationId.toString()))
                .assertThat("$.errorMessage", is("Failed to send notification. Gov.Notify responded with status 'permanent-failure'"))
                .assertThat("$.failedTime", is(notNullValue()))
        ;

    }

    @Test
    public void shouldSendSuccessfulEmailButFailedWithNotFound() {
        final UUID cppNotificationId = randomUUID();
        final UUID govNotifyNotificationId = randomUUID();

        stubGovNotifySuccessClientWithPersonalisationWithoutMaterialUrl(cppNotificationId, govNotifyNotificationId);
        stubGovNotifyCheckStatusWithStatusNotFound(cppNotificationId, govNotifyNotificationId);
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
        ;

        final Event failedEvent = events.get(1);
        assertThat(failedEvent.getName(), is("notificationnotify.events.notification-failed"));
        with(failedEvent.getMetadata())
                .assertThat("stream.id", is(cppNotificationId.toString()))
                .assertThat("stream.version", is(2));
        with(failedEvent.getPayload())
                .assertThat("$.notificationId", is(cppNotificationId.toString()))
                .assertThat("$.errorMessage", is("Failed to send notification. Check delivery status failed after 2 attempts. Permanent failure. Gov.Notify responded with status 'not found'"))
                .assertThat("$.failedTime", is(notNullValue()))
        ;
    }

    //Invalid Test to be Removed as validation happens on the send email not on check status
    //shouldSendSuccessfulEmailButFailedWithInvalidRequest - removed

    @Test
    public void shouldSendSuccessfulEmailButFailedWithUnexpected() {
        final UUID cppNotificationId = randomUUID();
        final UUID govNotifyNotificationId = randomUUID();
        stubGovNotifySuccessClientWithPersonalisationWithoutMaterialUrl(cppNotificationId, govNotifyNotificationId);
        stubGovNotifyCheckStatusWithStatusUnexpected(cppNotificationId, govNotifyNotificationId);
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
        ;

        final Event failedEvent = events.get(1);
        assertThat(failedEvent.getName(), is("notificationnotify.events.notification-failed"));
        with(failedEvent.getMetadata())
                .assertThat("stream.id", is(cppNotificationId.toString()))
                .assertThat("stream.version", is(2));

        with(failedEvent.getPayload())
                .assertThat("$.notificationId", is(cppNotificationId.toString()))
                .assertThat("$.errorMessage", is("Failed to send notification. Check delivery status failed after 2 attempts. Permanent failure. Gov.Notify responded with status 'unexpected'"))
                .assertThat("$.failedTime", is(notNullValue()))
        ;
    }

    private Response sendEmailNotificationJson(final String resource,
                                               final String mediaType,
                                               final String payload) {
        final MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add(USER_ID, NOTIFY_SYSTEM_USER);
        return restClient.postCommand(SEND_EMAIL_POST_URL + resource, mediaType, payload, headers);
    }
}
