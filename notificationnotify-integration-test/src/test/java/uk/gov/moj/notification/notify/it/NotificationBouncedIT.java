package uk.gov.moj.notification.notify.it;

import static java.lang.String.format;
import static java.util.Optional.empty;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static uk.gov.justice.services.common.http.HeaderConstants.USER_ID;
import static uk.gov.justice.services.integrationtest.utils.jms.JmsMessageConsumerClientProvider.newPublicJmsMessageConsumerClientProvider;
import static uk.gov.justice.services.test.utils.common.host.TestHostProvider.getHost;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyCheckStatusWithStatusDelivered;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifySuccessClientWithPersonalisationWithoutMaterialUrl;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubUsersGroups;
import static uk.gov.moj.notification.notify.it.util.PayloadGeneratorUtil.payloadWithPersonalisationWithoutMaterialUrl;

import uk.gov.justice.services.integrationtest.utils.jms.JmsMessageConsumerClient;
import uk.gov.justice.services.test.utils.core.messaging.Poller;
import uk.gov.justice.services.test.utils.core.rest.RestClient;

import java.security.Security;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;

import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.DummySSLSocketFactory;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import io.restassured.path.json.JsonPath;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Disabled
public class NotificationBouncedIT extends BaseIT {

    private static final String PORT = "8080";
    private static final String NOTIFY_SYSTEM_USER = randomUUID().toString();
    private static final String USER_PASSWORD = "Hux39937";
    private static final String USER_NAME = "test";
    private static final String EMAIL_USER_ADDRESS = "crime.sit.notifications4@HMCTS.NET";
    private static final String PUBLIC_NOTIFICATIONNOTIFY_EVENTS_EMAIL_NOTIFICATION_BOUNCED = "public.notificationnotify.events.email-notification-bounced";
    private static final String SEND_EMAIL_POST_CONTENT_TYPE = "application/vnd.notificationnotify.email+json";
    private static final String SEND_NOTIFICATION_POST_URL = format("http://%s:%s/notificationnotify-command-api/command/api/rest/notificationnotify/notifications/", getHost(), PORT);
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationBouncedIT.class);
    final UUID cppNotificationId = randomUUID();
    private final RestClient restClient = new RestClient();
    private final Poller poller = new Poller(5, 30000);
    private JmsMessageConsumerClient publicNotificationBouncedConsumerClient;
    private GreenMailUser user;
    private GreenMail greenMail;

    @BeforeEach
    public void setUp() throws Exception {
        stubUsersGroups();
        Security.setProperty("ssl.SocketFactory.provider", DummySSLSocketFactory.class.getName());
        ServerSetup setup = new ServerSetup(3143, "localhost", "imap");
        greenMail = new GreenMail(setup);
        user = greenMail.setUser(EMAIL_USER_ADDRESS, USER_NAME, USER_PASSWORD);
        greenMail.start();
        LOGGER.info("GREEN Mail Started");
        sendEmail();

        this.publicNotificationBouncedConsumerClient = newPublicJmsMessageConsumerClientProvider()
                .withEventNames(PUBLIC_NOTIFICATIONNOTIFY_EVENTS_EMAIL_NOTIFICATION_BOUNCED)
                .getMessageConsumerClient();

    }

    @AfterEach
    public void tearDown() {
        greenMail.stop();
        LOGGER.info("GREEN Mail stopped");
    }


    @Test
    @SuppressWarnings("squid:S2925")
    public void
    shouldCheckBouncedEmail() throws Exception {

        LOGGER.info("is GREEN Mail  Running" + greenMail.getImap().isRunning());
        // create an e-mail message using javax.mail
        MimeMessage message = new MimeMessage((Session) null);
        message.setFrom(new InternetAddress(EMAIL_USER_ADDRESS));
        message.addRecipient(Message.RecipientType.TO, new InternetAddress(
                EMAIL_USER_ADDRESS));
        message.setSubject("test");
        message.setText("test");
        message.setHeader("NotificationId", cppNotificationId.toString());

        MimeBodyPart messageBodyPart = new MimeBodyPart();
        messageBodyPart.setContent(message, "message/rfc822");
        messageBodyPart.setHeader("NotificationId", cppNotificationId.toString());
        // Add message body part to multi part
        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(messageBodyPart);
        MimeMessage message2 = new MimeMessage((Session) null);
        message2.setFrom(new InternetAddress(EMAIL_USER_ADDRESS));
        message2.addRecipient(Message.RecipientType.TO, new InternetAddress(EMAIL_USER_ADDRESS));
        message2.setSubject("test");
        message2.setText("test");
        message2.setHeader("NotificationId", cppNotificationId.toString());
        message2.setContent(multipart);
        user.deliver(message2);

        createFolders();

        String event = getNotificationBouncedEventByNotificationId().orElse(null);
        assertNotNull(event);

    }

    private void createFolders() throws Exception {
        final Properties properties = new Properties();
        final Session emailSession = Session.getDefaultInstance(properties);
        Store store = emailSession.getStore("imap");
        store.connect("localhost", 3143, USER_NAME, USER_PASSWORD);
        Folder archieveFolder = store.getFolder("Archive");
        archieveFolder.create(Folder.HOLDS_MESSAGES);
        Folder folder = store.getFolder("Junk Email");
        folder.create(Folder.HOLDS_MESSAGES);
    }

    private Optional<String> getNotificationBouncedEventByNotificationId() {

        return poller.pollUntilFound(() -> {
            final Optional<String> eventOptional = publicNotificationBouncedConsumerClient.retrieveMessage();
            if (eventOptional.isPresent()) {
                final String eventJson = eventOptional.get();
                final String foundNotificationId = new JsonPath(eventJson).getString("notificationId");

                if (foundNotificationId.equals(cppNotificationId.toString())) {
                    return eventOptional;
                }
            }

            return empty();
        });
    }

    public void sendEmail() {
        final UUID govNotifyNotificationId = randomUUID();
        stubGovNotifySuccessClientWithPersonalisationWithoutMaterialUrl(cppNotificationId, govNotifyNotificationId);
        stubGovNotifyCheckStatusWithStatusDelivered(cppNotificationId, govNotifyNotificationId);
        final Response response = sendEmailNotificationJson(cppNotificationId.toString(), SEND_EMAIL_POST_CONTENT_TYPE, payloadWithPersonalisationWithoutMaterialUrl());
        assertThat(response.getStatus(), Is.is(ACCEPTED.getStatusCode()));
    }

    private Response sendEmailNotificationJson(final String resource,
                                               final String mediaType,
                                               final String payload) {
        final MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add(USER_ID, NOTIFY_SYSTEM_USER);
        return restClient.postCommand(SEND_NOTIFICATION_POST_URL + resource, mediaType, payload, headers);
    }

}