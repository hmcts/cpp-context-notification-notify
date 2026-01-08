package uk.gov.moj.notification.notify.it;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.reset;
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
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyCheckStatusWithStatusDelivered;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifySuccessClientWithPersonalisationWithoutMaterialUrl;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubUsersGroups;
import static uk.gov.moj.notification.notify.it.util.PayloadGeneratorUtil.payloadWithPersonalisationWithoutMaterialUrl;

import uk.gov.justice.services.eventsourcing.repository.jdbc.event.Event;
import uk.gov.justice.services.test.utils.core.rest.RestClient;
import uk.gov.moj.notification.notify.it.util.EventFetcher;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NotifyMultipleEmailSendRestRequestIT {

    private static final String HOST = System.getProperty("INTEGRATION_HOST_KEY", "localhost");
    private static final String PORT = "8080";
    private static final String NOTIFY_SYSTEM_USER = randomUUID().toString();
    private static final String SEND_EMAIL_POST_URL = format("http://%s:%s/notificationnotify-command-api/command/api/rest/notificationnotify/notifications/", HOST, PORT);
    private static final String SEND_EMAIL_POST_CONTENT_TYPE = "application/vnd.notificationnotify.email+json";
    private final RestClient restClient = new RestClient();
    private EventFetcher eventFetcher = null;
    private final static int REQUEST_COUNT = 10;

    @BeforeAll
    public static void beforeAll() {
        configureFor(getHost(), 8080);
        reset();
    }

    @BeforeEach
    public void setUp() throws Exception {
        stubEnableAllCapabilities();
        stubUsersGroups();
        eventFetcher = new EventFetcher();
    }

    @Test
    public void shouldSendMultipleSuccessfulEmails() {

        List<UUID> notificationIds = new ArrayList<>();

        for (int i = 0; i < REQUEST_COUNT; i++) {
            final UUID cppNotificationId = randomUUID();

            final UUID govNotifyNotificationId = randomUUID();

            notificationIds.add(cppNotificationId);

            stubGovNotifySuccessClientWithPersonalisationWithoutMaterialUrl(cppNotificationId, govNotifyNotificationId);
            stubGovNotifyCheckStatusWithStatusDelivered(cppNotificationId, govNotifyNotificationId);

            final Response response = sendEmailNotificationJson(cppNotificationId.toString(), SEND_EMAIL_POST_CONTENT_TYPE, payloadWithPersonalisationWithoutMaterialUrl());

            assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
        }

        for (final UUID notificationId : notificationIds) {

            final int expectedNumberOfEvents = 2;
            final List<Event> events = eventFetcher.getEventsByStreamAndSize(notificationId, expectedNumberOfEvents);

            assertThat(events.size(), is(expectedNumberOfEvents));

            final Event queuedEvent = events.get(0);
            assertThat(queuedEvent.getName(), is("notificationnotify.events.notification-queued"));
            with(queuedEvent.getMetadata())
                    .assertThat("stream.id", is(notificationId.toString()))
                    .assertThat("stream.version", is(1));

            with(queuedEvent.getPayload())
                    .assertThat("$.notificationId", is(notificationId.toString()))
                    .assertThat("$.personalisation", is(notNullValue()))
            ;

            final Event sentEvent = events.get(1);
            assertThat(sentEvent.getName(), is("notificationnotify.events.notification-sent"));
            with(sentEvent.getMetadata())
                    .assertThat("stream.id", is(notificationId.toString()))
                    .assertThat("stream.version", is(2));

            with(sentEvent.getPayload())
                    .assertThat("$.notificationId", is(notificationId.toString()))
                    .assertThat("$.sentTime", is(notNullValue()))
            ;
        }
    }

    private Response sendEmailNotificationJson(final String resource,
                                               final String mediaType,
                                               final String payload) {
        final MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add(USER_ID, NOTIFY_SYSTEM_USER);
        return restClient.postCommand(SEND_EMAIL_POST_URL + resource, mediaType, payload, headers);
    }
}
