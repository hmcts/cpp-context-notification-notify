package uk.gov.moj.notification.notify.it;

import static com.jayway.jsonassert.JsonAssert.with;
import static java.lang.String.format;
import static java.util.Optional.empty;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.jupiter.api.Assertions.fail;
import static uk.gov.justice.services.common.http.HeaderConstants.USER_ID;
import static uk.gov.justice.services.integrationtest.utils.jms.JmsMessageConsumerClientProvider.newPublicJmsMessageConsumerClientProvider;
import static uk.gov.justice.services.test.utils.common.host.TestHostProvider.getHost;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.DOCUMENT_DOWNLOAD_URL;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubDocumentDownload;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubDocumentDownloadFailure;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubEnableAllCapabilities;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyTemporaryFailureForSendLetter;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubUsersGroups;
import static uk.gov.moj.notification.notify.it.util.FrameworkConstants.PUBLIC_EVENT_FAILED;
import static uk.gov.moj.notification.notify.it.util.ResourceLoader.getBase64EncodedFileContent;
import static uk.gov.moj.notification.notify.it.util.ResourceLoader.getFileContent;

import uk.gov.justice.services.integrationtest.utils.jms.JmsMessageConsumerClient;
import uk.gov.justice.services.test.utils.core.messaging.Poller;
import uk.gov.justice.services.test.utils.core.rest.RestClient;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;

import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LetterPublicEventIT extends BaseIT {

    private static final String PORT = "8080";
    private static final String NOTIFY_SYSTEM_USER = randomUUID().toString();
    private static final String SEND_NOTIFICATION_POST_URL = format("http://%s:%s/notificationnotify-command-api/command/api/rest/notificationnotify/notifications/", getHost(), PORT);
    private static final String SEND_LETTER_POST_CONTENT_TYPE = "application/vnd.notificationnotify.letter+json";

    private final RestClient restClient = new RestClient();
    private final Poller poller = new Poller();
    private JmsMessageConsumerClient publicNotificationFailedConsumerClient = newPublicJmsMessageConsumerClientProvider()
            .withEventNames(PUBLIC_EVENT_FAILED)
            .getMessageConsumerClient();


    @BeforeEach
    public void setUp() throws Exception {
        stubEnableAllCapabilities();
        stubUsersGroups();
    }

    @Test
    public void shouldEmitAPublicFailureEventIfGovNotifyReturnsAFailureResponse() throws IOException {

        final UUID cppNotificationId = randomUUID();
        final String filePath = "pdf/JohnBloggs.pdf";
        stubDocumentDownload(getFileContent(filePath));

        stubGovNotifyTemporaryFailureForSendLetter(cppNotificationId, getBase64EncodedFileContent(filePath));

        final String payload = createObjectBuilder()
                .add("letterUrl", DOCUMENT_DOWNLOAD_URL)
                .build().toString();
        final MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add(USER_ID, NOTIFY_SYSTEM_USER);

        final String url = SEND_NOTIFICATION_POST_URL + cppNotificationId;

        final Response response = restClient.postCommand(
                url,
                SEND_LETTER_POST_CONTENT_TYPE,
                payload,
                headers);

            assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final Optional<String> publicFailedEvent = getEventByNotificationId(cppNotificationId);

        if (publicFailedEvent.isPresent()) {

            final String json = publicFailedEvent.get();

            with(json)
                    .assertThat("$._metadata.name", is("public.notificationnotify.events.notification-failed"))
                    .assertThat("notificationId", is(cppNotificationId.toString()))
                    .assertThat("errorMessage", is("Failed to send notification. Failed to send-letter after 3 attempts. Gov.Notify responded with 'Status code: 500 Internal Server Error'"))
                    .assertThat("statusCode", is(500))
                    .assertThat("failedTime", is(notNullValue()))
            ;

        } else {
            fail();
        }
    }

    @Test
    public void shouldEmitAPublicFailureEventIfTheLetterPdfCannotBeDownloaded() {
        final UUID cppNotificationId = randomUUID();
        stubDocumentDownloadFailure();

        final String payload = createObjectBuilder()
                .add("letterUrl", DOCUMENT_DOWNLOAD_URL)
                .build().toString();
        final MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add(USER_ID, NOTIFY_SYSTEM_USER);

        final String url = SEND_NOTIFICATION_POST_URL + cppNotificationId;
        final Response response = restClient.postCommand(
                url,
                SEND_LETTER_POST_CONTENT_TYPE,
                payload,
                headers);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final Optional<String> publicFailedEvent = getEventByNotificationId(cppNotificationId);

        if (publicFailedEvent.isPresent()) {
            final String json = publicFailedEvent.get();

            with(json)
                    .assertThat("$._metadata.name", is("public.notificationnotify.events.notification-failed"))
                    .assertThat("notificationId", is(cppNotificationId.toString()))
                    .assertThat("errorMessage", is("Failed to send notification. Failed to send-letter after 3 attempts. Failed to download pdf. Error message: 'Failed to download PDF document: Response code '500''"))
                    .assertThat("statusCode", is(500))
                    .assertThat("failedTime", is(notNullValue()))
            ;
        } else {
            fail();
        }
    }

    private Optional<String> getEventByNotificationId(final UUID notificationId) {

        return poller.pollUntilFound(() -> {
            final Optional<String> eventOptional = publicNotificationFailedConsumerClient.retrieveMessage();
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
