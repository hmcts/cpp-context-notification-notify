package uk.gov.moj.notification.notify.it;

import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static javax.json.Json.createObjectBuilder;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static uk.gov.justice.services.common.http.HeaderConstants.USER_ID;
import static uk.gov.justice.services.integrationtest.utils.jms.JmsMessageConsumerClientProvider.newPublicJmsMessageConsumerClientProvider;
import static uk.gov.justice.services.test.utils.common.host.TestHostProvider.getHost;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.DOCUMENT_DOWNLOAD_URL;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubDocumentDownload;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubEnableAllCapabilities;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyCheckLetterStatusWithStatusReceived;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifySendLetterWithStatusDelivered;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubUsersGroups;
import static uk.gov.moj.notification.notify.it.util.FrameworkConstants.PUBLIC_EVENT_SENT;
import static uk.gov.moj.notification.notify.it.util.ResourceLoader.getBase64EncodedFileContent;
import static uk.gov.moj.notification.notify.it.util.ResourceLoader.getFileContent;

import uk.gov.justice.services.integrationtest.utils.jms.JmsMessageConsumerClient;
import uk.gov.justice.services.test.utils.core.rest.RestClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.path.json.JsonPath;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotifyMultipleLetterSendRestRequestIT extends BaseIT{

    private static final Logger LOGGER = LoggerFactory.getLogger(NotifyMultipleLetterSendRestRequestIT.class);

    private static final String PORT = "8080";
    private static final String NOTIFY_SYSTEM_USER = randomUUID().toString();
    private static final String SEND_NOTIFICATION_POST_URL = format("http://%s:%s/notificationnotify-command-api/command/api/rest/notificationnotify/notifications/", getHost(), PORT);
    private static final String SEND_LETTER_POST_CONTENT_TYPE = "application/vnd.notificationnotify.letter+json";
    private static final String PDF_FILE_PATH = "pdf/JohnBloggs.pdf";

    private final RestClient restClient = new RestClient();
    private final StopWatch stopWatch = new StopWatch();
    private JmsMessageConsumerClient publicNotificationSentConsumerClient;

    private final static int MESSAGE_COUNT = 10;


    @BeforeEach
    public void setUp() throws Exception {
        stubEnableAllCapabilities();
        stubUsersGroups();

        this.publicNotificationSentConsumerClient = newPublicJmsMessageConsumerClientProvider()
                .withEventNames(PUBLIC_EVENT_SENT)
                .getMessageConsumerClient();
    }

    @SuppressWarnings("squid:S1607")
    @Disabled
    @Test
    public void shouldSendMultipleLettersSuccessfully() throws Exception {

        final Map<UUID, UUID> notificationMappings = new HashMap<>();
        final Set<UUID> publicSentEventNotificationIds = new HashSet<>();

        createNotificationMappings(notificationMappings);
        createStubs(notificationMappings);
        sendLetterNotifications(notificationMappings);

        for (int i = 0; i < MESSAGE_COUNT; i++) {
            final Optional<String> publicSentEvent = publicNotificationSentConsumerClient.retrieveMessage();
            if (publicSentEvent.isPresent()) {
                final JsonPath jsonPathPublicSentEvent = new JsonPath(publicSentEvent.get());
                final UUID notificationId = UUID.fromString(jsonPathPublicSentEvent.get("notificationId"));
                publicSentEventNotificationIds.add(notificationId);
            } else {
                fail();
            }

        }

        assertThat(publicSentEventNotificationIds.size(), is(MESSAGE_COUNT));

        stopWatch.stop();

        for (final UUID publicSentEventNotificationId : publicSentEventNotificationIds) {
            assertTrue(notificationMappings.keySet().contains(publicSentEventNotificationId));
        }

        LOGGER.info("Letters sent to GovNotify: " + MESSAGE_COUNT);
        LOGGER.info("Public events raised in : " + MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds");
    }

    private void sendLetterNotifications(final Map<UUID, UUID> notificationMapping) {

        final String payload = createObjectBuilder().add("letterUrl", DOCUMENT_DOWNLOAD_URL).build().toString();

        stopWatch.start();

        for (final UUID cppNotificationId : notificationMapping.keySet()) {
            final MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
            headers.add(USER_ID, NOTIFY_SYSTEM_USER);
            final Response response = restClient.postCommand(SEND_NOTIFICATION_POST_URL + cppNotificationId.toString(), SEND_LETTER_POST_CONTENT_TYPE, payload, headers);
            assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
        }
    }

    private void createNotificationMappings(final Map<UUID, UUID> notificationMapping) {
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            final UUID cppNotificationId = randomUUID();
            final UUID govNotifyNotificationId = randomUUID();

            notificationMapping.put(cppNotificationId, govNotifyNotificationId);
        }
    }

    private void createStubs(final Map<UUID, UUID> notificationMappings) throws IOException {

        stubDocumentDownload(getFileContent(PDF_FILE_PATH));

        for (final Map.Entry<UUID, UUID> entry : notificationMappings.entrySet()) {
            stubGovNotifySendLetterWithStatusDelivered(entry.getKey(), entry.getValue(), getBase64EncodedFileContent(PDF_FILE_PATH));
            stubGovNotifyCheckLetterStatusWithStatusReceived(entry.getKey(), entry.getValue());
        }
    }
}
