package uk.gov.moj.notification.notify.it;

import static com.jayway.jsonassert.JsonAssert.with;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.fail;
import static uk.gov.justice.services.common.http.HeaderConstants.USER_ID;
import static uk.gov.justice.services.test.utils.common.host.TestHostProvider.getHost;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubUsersGroups;
import static uk.gov.justice.services.integrationtest.utils.jms.JmsMessageConsumerClientProvider.newPrivateJmsMessageConsumerClientProvider;

import uk.gov.justice.services.integrationtest.utils.jms.JmsMessageConsumerClient;
import uk.gov.justice.services.test.utils.core.messaging.Poller;
import uk.gov.justice.services.test.utils.core.rest.RestClient;

import java.util.Optional;
import java.util.UUID;

import javax.json.JsonObject;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class SendLetterCommandIT extends BaseIT {

    private static final String PORT = "8080";
    private static final String NOTIFY_SYSTEM_USER =  randomUUID().toString();
    public static final String SEND_EMAIL_POST_URL = format("http://%s:%s/notificationnotify-command-api/command/api/rest/notificationnotify/notifications/", getHost(), PORT);
    private static final String SEND_EMAIL_POST_CONTENT_TYPE = "application/vnd.notificationnotify.letter+json";
    private static final String LETTER_NOTIFICATION_QUEUED_EVENT_NAME = "notificationnotify.events.letter-queued";
    private static final String FIRST_CLASS_LETTER_NOTIFICATION_QUEUED_EVENT_NAME = "notificationnotify.events.first-class-letter-queued";
    private static final String LETTER_DOWNLOAD_URL = "http://localhost:8080/some-context/somewhere/letter/90fe9fe3-0e03-40c3-8298-ec6f55323582";

    private final Poller poller = new Poller(10, 500);

    @BeforeAll
    public static void configureWiremock() throws Exception {
        stubUsersGroups();
    }

    @Test
    public void shouldHandleSendLetterCommand() throws Exception {
        final JmsMessageConsumerClient letterNotificationQueuedConsumer = newPrivateJmsMessageConsumerClientProvider(CONTEXT_NAME)
                .withEventNames(LETTER_NOTIFICATION_QUEUED_EVENT_NAME)
                .getMessageConsumerClient();

        final UUID notificationId = randomUUID();

        sendCommand(notificationId, "second");

        final Optional<String> message = poller.pollUntilFound(letterNotificationQueuedConsumer::retrieveMessageNoWait);

        if (message.isPresent()) {
            final String json = message.get();

            with(json)
                    .assertThat("$._metadata.name", is(LETTER_NOTIFICATION_QUEUED_EVENT_NAME))
                    .assertThat("$.notificationId", is(notificationId.toString()))
                    .assertThat("$.letterUrl", is(LETTER_DOWNLOAD_URL));
        } else {
            fail();
        }
    }

    @Test
    public void shouldHandleSendFirstClassLetterCommand() throws Exception {
        final JmsMessageConsumerClient firstClassLetterNotificationQueuedConsumer = newPrivateJmsMessageConsumerClientProvider(CONTEXT_NAME)
                .withEventNames(FIRST_CLASS_LETTER_NOTIFICATION_QUEUED_EVENT_NAME)
                .getMessageConsumerClient();

        final UUID notificationId = randomUUID();

        sendCommand(notificationId, "first");

        final Optional<String> message = poller.pollUntilFound(firstClassLetterNotificationQueuedConsumer::retrieveMessageNoWait);

        if (message.isPresent()) {
            final String json = message.get();

            with(json)
                    .assertThat("$._metadata.name", is(FIRST_CLASS_LETTER_NOTIFICATION_QUEUED_EVENT_NAME))
                    .assertThat("$.notificationId", is(notificationId.toString()))
                    .assertThat("$.letterUrl", is(LETTER_DOWNLOAD_URL));
        } else {
            fail();
        }
    }

    public static Response sendCommand(final UUID notificationId, final String postage) {
        final String url = SEND_EMAIL_POST_URL + notificationId;
        final JsonObject payload = createObjectBuilder()
                .add("letterUrl", LETTER_DOWNLOAD_URL)
                .add("postage", postage)
                .build();

        final MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add(USER_ID, NOTIFY_SYSTEM_USER);
        return new RestClient().postCommand(url, SEND_EMAIL_POST_CONTENT_TYPE, payload.toString(), headers);
    }
}
