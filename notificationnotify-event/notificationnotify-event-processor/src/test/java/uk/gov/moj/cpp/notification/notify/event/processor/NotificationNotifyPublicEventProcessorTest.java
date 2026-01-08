package uk.gov.moj.cpp.notification.notify.event.processor;

import static com.jayway.jsonassert.JsonAssert.with;
import static java.util.Optional.of;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.spi.DefaultJsonMetadata.metadataBuilder;
import static uk.gov.justice.services.test.utils.core.matchers.UuidStringMatcher.isAUuid;
import static uk.gov.justice.services.test.utils.core.messaging.JsonEnvelopeBuilder.envelope;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;

import uk.gov.justice.json.schemas.domains.notificationnotify.EmailNotificationBounced;
import uk.gov.justice.json.schemas.domains.notificationnotify.NotificationSent;
import uk.gov.justice.json.schemas.domains.notificationnotify.PocaEmailAlreadyReceived;
import uk.gov.justice.json.schemas.domains.notificationnotify.PocaEmailNotificationReceived;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.ZonedDateTimes;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.fileservice.api.FileServiceException;
import uk.gov.justice.services.fileservice.api.FileStorer;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.systemusers.ServiceContextSystemUserProvider;

import java.time.ZonedDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class NotificationNotifyPublicEventProcessorTest {

    private static final String NOTIFICATION_MARK_AS_SENT_PUBLIC_EVENT = "public.notificationnotify.events.notification-sent";
    private static final String NOTIFICATION_MARK_AS_FAILED_PUBLIC_EVENT = "public.notificationnotify.events.notification-failed";

    @Mock
    private Sender sender;

    @Spy
    private final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    @Spy
    private ObjectToJsonObjectConverter objectToJsonObjectConverter = new ObjectToJsonObjectConverter(objectMapper);

    @Mock
    private FileStorer fileStorer;

    @InjectMocks
    private NotificationNotifyPublicEventProcessor notificationNotifyEventProcessor;

    @Captor
    private ArgumentCaptor<Envelope> jsonEnvelopeCaptor;

    @Mock
    private ServiceContextSystemUserProvider serviceContextSystemUserProvider;

    @BeforeEach
    public void setup() {
        setField(objectToJsonObjectConverter, "mapper", new ObjectMapperProducer().objectMapper());
    }

    @Test
    public void shouldSendPublicMarkAsSentEvent() {

        final NotificationSent notificationSent = NotificationSent.notificationSent()
                .withClientContext("correspondence")
                .withNotificationId(UUID.fromString("5c5a1d30-0414-11e7-93ae-92361f002671"))
                .withSentTime(ZonedDateTime.parse("2016-07-11T12:55:28.180Z"))
                .build();


        final Envelope<NotificationSent> eventEnvelope = envelopeFrom(
                metadataBuilder().withName("notificationnotify.events.notification-sent")
                        .withUserId(String.valueOf(randomUUID()))
                        .withId(randomUUID()).build(),
                notificationSent);

        when(serviceContextSystemUserProvider.getContextSystemUserId()).thenReturn(of(UUID.randomUUID()));
        notificationNotifyEventProcessor.notificationSuccess(eventEnvelope);
        verify(sender).send(jsonEnvelopeCaptor.capture());

        final Envelope publicEventEnvelope = jsonEnvelopeCaptor
                .getValue();

        assertThat(publicEventEnvelope.metadata().name(), is(NOTIFICATION_MARK_AS_SENT_PUBLIC_EVENT));
        assertThat(publicEventEnvelope.metadata().id().toString(), isAUuid());

    }

    @Test
    public void shouldSendPublicMarkAsFailedEvent() {

        final String notificationId = randomUUID().toString();
        final String failedTime = ZonedDateTimes.toString(new UtcClock().now());
        final String errorMessage = "error message";
        final int statusCode = SC_NOT_FOUND;

        final JsonEnvelope eventEnvelope = envelope()
                .with(metadataBuilder()
                        .withName("notificationnotify.events.notification-failed")
                        .withId(randomUUID()))
                .withPayloadOf(notificationId, "notificationId")
                .withPayloadOf(failedTime, "failedTime")
                .withPayloadOf(errorMessage, "errorMessage")
                .withPayloadOf(statusCode, "statusCode")
                .withPayloadOf("correspondence", "clientContext")
                .build();


        notificationNotifyEventProcessor.notificationFailed(eventEnvelope);

        verify(sender).send(jsonEnvelopeCaptor.capture());

        final Envelope publicEventEnvelope = jsonEnvelopeCaptor
                .getValue();

        assertThat(publicEventEnvelope.metadata().name(), is(NOTIFICATION_MARK_AS_FAILED_PUBLIC_EVENT));
        assertThat(publicEventEnvelope.metadata().id().toString(), isAUuid());

        final String payloadJson = publicEventEnvelope
                .payload()
                .toString();

        with(payloadJson)
                .assertThat("notificationId", is(notificationId))
                .assertThat("failedTime", is(failedTime))
                .assertThat("errorMessage", is(errorMessage))
                .assertThat("statusCode", is(statusCode))
        ;
    }

    @Test
    public void shouldSendPublicMarkAsFailedEventWithoutHttpStatusCode() {

        final String notificationId = randomUUID().toString();
        final String failedTime = ZonedDateTimes.toString(new UtcClock().now());
        final String errorMessage = "error message";

        final JsonEnvelope eventEnvelope = envelope()
                .with(metadataBuilder()
                        .withName("notificationnotify.events.notification-failed")
                        .withId(randomUUID()))
                .withPayloadOf(notificationId, "notificationId")
                .withPayloadOf(failedTime, "failedTime")
                .withPayloadOf(errorMessage, "errorMessage")
                .withPayloadOf("correspondence", "clientContext")
                .build();


        notificationNotifyEventProcessor.notificationFailed(eventEnvelope);

        verify(sender).send(jsonEnvelopeCaptor.capture());

        final Envelope publicEventEnvelope = jsonEnvelopeCaptor
                .getValue();

        assertThat(publicEventEnvelope.metadata().name(), is(NOTIFICATION_MARK_AS_FAILED_PUBLIC_EVENT));
        assertThat(publicEventEnvelope.metadata().id().toString(), isAUuid());

        final String payloadJson = publicEventEnvelope
                .payload()
                .toString();

        with(payloadJson)
                .assertThat("notificationId", is(notificationId))
                .assertThat("failedTime", is(failedTime))
                .assertThat("errorMessage", is(errorMessage))
                .assertNotDefined("statusCode")
        ;
    }

    @Test
    public void shouldSendPublicNotificationBouncedEvent() {

        UUID notificationId = randomUUID();
        final Envelope<EmailNotificationBounced> eventEnvelope = envelopeFrom(
                metadataBuilder().withName("notificationnotify.events.email-notification-bounced").withId(randomUUID()).build(),
                EmailNotificationBounced.emailNotificationBounced().withNotificationId(notificationId).build());

        notificationNotifyEventProcessor.notificationBounced(eventEnvelope);
        verify(sender).send(jsonEnvelopeCaptor.capture());

        final Envelope publicEventEnvelope = jsonEnvelopeCaptor.getValue();

        assertThat(publicEventEnvelope.metadata().name(), is("public.notificationnotify.events.email-notification-bounced"));
        assertThat(publicEventEnvelope.metadata().id().toString(), isAUuid());
        final EmailNotificationBounced payloadJson = (EmailNotificationBounced) publicEventEnvelope.payload();
        assertThat(payloadJson.getNotificationId(), is(notificationId));

    }

    @Test
    public void shouldSendPocaEmailNotificationReceivedEvent() {

        final UUID pocaFileId = randomUUID();
        final UUID pocaMailId = randomUUID();
        final String pocaEmail = "test@test.com";
        final String subject = "test";

        final Envelope<PocaEmailNotificationReceived> eventEnvelope = envelopeFrom(
                metadataBuilder().withName("notificationnotify.events.poca-email-notification-received").withId(randomUUID()).build(),
                PocaEmailNotificationReceived.pocaEmailNotificationReceived()
                        .withPocaFileId(pocaFileId)
                        .withPocaMailId(pocaMailId)
                        .withPocaEmail(pocaEmail)
                        .withEmailSubject(subject)
                        .build());

        notificationNotifyEventProcessor.pocaEmailReceived(eventEnvelope);
        verify(sender).send(jsonEnvelopeCaptor.capture());

        final Envelope publicEventEnvelope = jsonEnvelopeCaptor.getValue();

        assertThat(publicEventEnvelope.metadata().name(), is("public.notificationnotify.events.poca-email-notification-received"));
        assertThat(publicEventEnvelope.metadata().id().toString(), isAUuid());

        final PocaEmailNotificationReceived payloadJson = (PocaEmailNotificationReceived) publicEventEnvelope.payload();

        assertThat(payloadJson.getPocaFileId(), is(pocaFileId));
        assertThat(payloadJson.getPocaMailId(), is(pocaMailId));
        assertThat(payloadJson.getPocaEmail(), is(pocaEmail));
        assertThat(payloadJson.getEmailSubject().get(), is(subject));
    }

    @Test
    public void shouldDeleteAssociatedFileWhenPocaEmailAlreadyNotified() throws FileServiceException {
        UUID pocaFileId = randomUUID();
        final Envelope<PocaEmailAlreadyReceived> eventEnvelope = envelopeFrom(
                metadataBuilder().withName("notificationnotify.events.poca-email-already-received").withId(randomUUID()).build(),
                PocaEmailAlreadyReceived.pocaEmailAlreadyReceived().withPocaFileId(pocaFileId).build());

        notificationNotifyEventProcessor.pocaEmailAlreadyReceived(eventEnvelope);

        verify(fileStorer).delete(pocaFileId);
    }
}
