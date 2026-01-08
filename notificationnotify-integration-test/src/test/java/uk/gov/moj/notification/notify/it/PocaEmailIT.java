package uk.gov.moj.notification.notify.it;

import static java.util.Objects.nonNull;
import static java.util.Optional.empty;
import static org.junit.Assert.assertNotNull;
import static uk.gov.justice.services.integrationtest.utils.jms.JmsMessageConsumerClientProvider.newPublicJmsMessageConsumerClientProvider;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubUsersGroups;
import static uk.gov.moj.notification.notify.it.util.FrameworkConstants.CONTEXT_NAME;

import uk.gov.justice.services.integrationtest.utils.jms.JmsMessageConsumerClient;
import uk.gov.justice.services.test.utils.core.messaging.Poller;
import uk.gov.justice.services.test.utils.persistence.DatabaseCleaner;

import java.io.File;
import java.security.Security;
import java.util.Optional;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.DummySSLSocketFactory;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Disabled
public class PocaEmailIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(PocaEmailIT.class);

    private static final String USER_PASSWORD = "Hux39937";
    private static final String USER_NAME = "poca";
    private static final String EMAIL_USER_ADDRESS = "crime.sit.notifications4@HMCTS.NET";
    private static final String PUBLIC_TOPIC_NAME = "public.event";
    private static final String NOTIFICATIONNOTIFY_EVENTS_POCA_EMAIL_NOTIFICATION_RECEIVED = "public.notificationnotify.events.poca-email-notification-received";

    private GreenMailUser user;
    private GreenMail greenMail;
    private JmsMessageConsumerClient publicPocaEmailNotificationReceivedtConsumerClient;
    private final Poller poller = new Poller(5, 30000);

    @BeforeEach
    public void setUp() throws Exception {
        final DatabaseCleaner databaseCleaner = new DatabaseCleaner();
        databaseCleaner.cleanEventStoreTables(CONTEXT_NAME);

        stubUsersGroups();
        Security.setProperty("ssl.SocketFactory.provider", DummySSLSocketFactory.class.getName());
        ServerSetup setup = new ServerSetup(3143, "localhost", "imap");
        greenMail = new GreenMail(setup);
        user = greenMail.setUser(EMAIL_USER_ADDRESS, USER_NAME, USER_PASSWORD);
        greenMail.start();
        LOGGER.info("GREEN Mail Started");

        this.publicPocaEmailNotificationReceivedtConsumerClient = newPublicJmsMessageConsumerClientProvider()
                .withEventNames(NOTIFICATIONNOTIFY_EVENTS_POCA_EMAIL_NOTIFICATION_RECEIVED)
                .getMessageConsumerClient();

    }

    @Test
    @SuppressWarnings("squid:S2925")
    public void shouldCheckPocaEmailNotificationReceived() throws Exception {

        LOGGER.info("is GREEN Mail  Running - " + greenMail.getImap().isRunning());

        MimeMessage message = new MimeMessage((Session) null);
        message.setFrom(new InternetAddress(EMAIL_USER_ADDRESS));
        message.addRecipient(Message.RecipientType.TO, new InternetAddress(EMAIL_USER_ADDRESS));
        message.setSubject("test");
        message.setText("test");

        final MimeBodyPart  messageBodyPart = new MimeBodyPart();
        Multipart multipart = new MimeMultipart();
        File file = new File(getClass().getClassLoader().getResource("docx/iw033-eng-new.docx").getFile());
        String fileName = "iw033-eng-new.docx";
        DataSource source = new FileDataSource(file);
        messageBodyPart.setDataHandler(new DataHandler(source));
        messageBodyPart.setFileName(fileName);
        multipart.addBodyPart(messageBodyPart);

        message.setContent(multipart);
        createFolders();
        user.deliver(message);

        String event = checkPocaEmailReceivedEventRaised().orElse(null);
        assertNotNull(event);

    }

    private Optional<String> checkPocaEmailReceivedEventRaised() {
        return poller.pollUntilFound(() -> {
            final Optional<String> eventOptional = publicPocaEmailNotificationReceivedtConsumerClient.retrieveMessage();
            if (eventOptional.isPresent()) {
                final String eventJson = eventOptional.get();
                final String pocaFileId = new JsonPath(eventJson).getString("pocaFileId");

                if (nonNull(pocaFileId)) {
                    return eventOptional;
                }
            }
            return empty();
        });
    }


    @AfterEach
    public void tearDown() {
        greenMail.stop();
        LOGGER.info("GREEN Mail stopped");
    }

    private void createFolders() throws Exception {
        final Properties properties = new Properties();
        final Session emailSession = Session.getDefaultInstance(properties);
        Store store = emailSession.getStore("imap");
        store.connect("localhost", 3143, USER_NAME, USER_PASSWORD);
        Folder archiveFolder = store.getFolder("Archive");
        archiveFolder.create(Folder.HOLDS_MESSAGES);
        Folder folder = store.getFolder("Junk Email");
        folder.create(Folder.HOLDS_MESSAGES);
    }
}