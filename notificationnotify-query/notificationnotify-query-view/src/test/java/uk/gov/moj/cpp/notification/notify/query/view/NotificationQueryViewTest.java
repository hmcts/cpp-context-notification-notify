package uk.gov.moj.cpp.notification.notify.query.view;

import static com.jayway.jsonassert.JsonAssert.with;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.test.utils.core.enveloper.EnveloperFactory.createEnveloper;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;

import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.ZonedDateTimes;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.test.utils.core.messaging.JsonEnvelopeBuilder;
import uk.gov.justice.services.test.utils.core.messaging.MetadataBuilderFactory;
import uk.gov.moj.cpp.notification.entity.Notification;
import uk.gov.moj.cpp.notification.entity.NotificationType;
import uk.gov.moj.cpp.notification.factory.NotificationFactory;
import uk.gov.moj.cpp.notification.repository.NotificationRepository;

import java.time.ZonedDateTime;
import java.util.UUID;

import javax.json.JsonValue;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class NotificationQueryViewTest {

    @Spy
    private Enveloper enveloper = createEnveloper();

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationQueryView notificationQueryView;

    @BeforeEach
    public void setup() throws IllegalAccessException {
        final ObjectToJsonObjectConverter objectToJsonObjectConverter = new ObjectToJsonObjectConverter();
        setField(objectToJsonObjectConverter, "mapper", new ObjectMapperProducer().objectMapper());
        notificationQueryView.converter = objectToJsonObjectConverter;
    }

    @Test
    public void shouldFindEmailNotificationById() {
        final UUID notificationId = randomUUID();
        final ZonedDateTime timestamp = new UtcClock().now();

        final Notification notification = createEmailNotificationWith(notificationId, "queued", "materialUrl", "sendto@address.com", "error", 202, timestamp, timestamp);
        when(notificationRepository.findBy(notificationId)).thenReturn(notification);

        final JsonEnvelope queryEnvelope = JsonEnvelopeBuilder.envelope().with(MetadataBuilderFactory.metadataWithDefaults())
                .withPayloadOf(notificationId, "notificationId")
                .build();

        final JsonEnvelope result = notificationQueryView.getNotificationById(queryEnvelope);

        assertThat(result, is(notNullValue()));

        with(result.payloadAsJsonObject().toString())
                .assertThat("$.notificationId", equalTo(notificationId.toString()))
                .assertThat("$.status", equalTo("queued"))
                .assertThat("$.errorMessage", equalTo("error"))
                .assertThat("$.sendToAddress", equalTo("sendto@address.com"))
                .assertThat("$.dateCreated", equalTo(ZonedDateTimes.toString(timestamp)))
                .assertThat("$.lastUpdated", equalTo(ZonedDateTimes.toString(timestamp)))
                .assertThat("$.statusCode", equalTo(202))
                .assertThat("$.materialUrl", equalTo("materialUrl"))
                .assertThat("$.notificationType", equalTo("EMAIL"));
    }

    @Test
    public void shouldFindLetterNotificationById() {
        final UUID notificationId = randomUUID();
        final ZonedDateTime timestamp = new UtcClock().now();
        final String letterUrl= "http://localhost";

        final Notification notification = createLetterNotificationWith(notificationId, "queued", letterUrl, timestamp, timestamp);
        when(notificationRepository.findBy(notificationId)).thenReturn(notification);

        final JsonEnvelope queryEnvelope = JsonEnvelopeBuilder.envelope().with(MetadataBuilderFactory.metadataWithDefaults())
                .withPayloadOf(notificationId, "notificationId")
                .build();

        final JsonEnvelope result = notificationQueryView.getNotificationById(queryEnvelope);

        assertThat(result, is(notNullValue()));

        with(result.payloadAsJsonObject().toString())
                .assertThat("$.notificationId", equalTo(notificationId.toString()))
                .assertThat("$.status", equalTo("queued"))
                .assertThat("$.dateCreated", equalTo(ZonedDateTimes.toString(timestamp)))
                .assertThat("$.lastUpdated", equalTo(ZonedDateTimes.toString(timestamp)))
                .assertThat("$.notificationType", equalTo("LETTER"));
    }

    @Test
    public void shouldNotFindNotificationWithNonMatchingId() {
        final UUID notificationId = randomUUID();
        when(notificationRepository.findBy(notificationId)).thenReturn(null);

        final JsonEnvelope queryEnvelope = JsonEnvelopeBuilder.envelope().with(MetadataBuilderFactory.metadataWithDefaults())
                .withPayloadOf(notificationId, "notificationId")
                .build();

        final JsonEnvelope result = notificationQueryView.getNotificationById(queryEnvelope);

        assertThat(result, is(notNullValue()));

        assertThat(result.payload(), is(JsonValue.NULL));
    }

    @Test
    public void shouldFindNotificationByNotificationTypeUsingQueryParam() {
        final UUID notificationId = randomUUID();
        final ZonedDateTime timestamp = new UtcClock().now();

        final Notification notification = createLetterNotificationWith(notificationId, "queued", "http://localhost", timestamp, timestamp);

        final JsonEnvelope queryEnvelope = JsonEnvelopeBuilder.envelope().with(MetadataBuilderFactory.metadataWithDefaults())
                .withPayloadOf(notificationId, "notificationId")
                .withPayloadOf(NotificationType.LETTER.name(), "notificationType")
                .build();


        when(notificationRepository.findNotifications(anyMap())).thenReturn(Lists.newArrayList(notification));
        final JsonEnvelope result = notificationQueryView.findNotification(queryEnvelope);

        assertThat(result, is(notNullValue()));

        with(result.payloadAsJsonObject().toString())
                .assertThat("$.notifications[0].notificationId", equalTo(notificationId.toString()))
                .assertThat("$.notifications[0].status", equalTo("queued"))
                .assertThat("$.notifications[0].dateCreated", equalTo(ZonedDateTimes.toString(timestamp)))
                .assertThat("$.notifications[0].lastUpdated", equalTo(ZonedDateTimes.toString(timestamp)))
                .assertThat("$.notifications[0].notificationType", equalTo("LETTER"))
                .assertThat("$.notifications[0].letterUrl", equalTo("http://localhost"));
    }

    @Test
    public void shouldFindNotificationByLetterUrlUsingQueryParam() {
        final UUID notificationAId = randomUUID();
        final UUID notificationBId = randomUUID();
        final ZonedDateTime timestamp = new UtcClock().now();
        final String letterUrl = "http://localhost";

        final Notification notificationA = createLetterNotificationWith(notificationAId, "queued", letterUrl, timestamp, timestamp);
        final Notification notificationB = createLetterNotificationWith(notificationBId, "queued", letterUrl, timestamp, timestamp);

        final JsonEnvelope queryEnvelope = JsonEnvelopeBuilder.envelope().with(MetadataBuilderFactory.metadataWithDefaults())
                .withPayloadOf(letterUrl, "letterUrl")
                .build();


        when(notificationRepository.findNotifications(anyMap())).thenReturn(Lists.newArrayList(notificationA, notificationB));
        final JsonEnvelope result = notificationQueryView.findNotification(queryEnvelope);

        assertThat(result, is(notNullValue()));

        with(result.payloadAsJsonObject().toString())
                .assertThat("$.notifications[0].notificationId", equalTo(notificationAId.toString()))
                .assertThat("$.notifications[0].status", equalTo("queued"))
                .assertThat("$.notifications[0].dateCreated", equalTo(ZonedDateTimes.toString(timestamp)))
                .assertThat("$.notifications[0].lastUpdated", equalTo(ZonedDateTimes.toString(timestamp)))
                .assertThat("$.notifications[0].notificationType", equalTo("LETTER"))
                .assertThat("$.notifications[0].letterUrl", equalTo("http://localhost"))

                .assertThat("$.notifications[1].notificationId", equalTo(notificationBId.toString()))
                .assertThat("$.notifications[1].status", equalTo("queued"))
                .assertThat("$.notifications[1].dateCreated", equalTo(ZonedDateTimes.toString(timestamp)))
                .assertThat("$.notifications[1].lastUpdated", equalTo(ZonedDateTimes.toString(timestamp)))
                .assertThat("$.notifications[1].notificationType", equalTo("LETTER"))
                .assertThat("$.notifications[1].letterUrl", equalTo("http://localhost"));
    }

    private Notification createEmailNotificationWith(final UUID notificationId,
                                                     final String status,
                                                     final String materialUrl,
                                                     final String sendToAddress,
                                                     final String errorMessage,
                                                     final int statusCode,
                                                     final ZonedDateTime dateCreated,
                                                     final ZonedDateTime lastUpdated
    ) {
        final Notification notification = new NotificationFactory().createEmailNotification(notificationId, status, materialUrl, sendToAddress, dateCreated, lastUpdated);

        notification.setErrorMessage(errorMessage);
        notification.setStatusCode(statusCode);
        return notification;
    }

    private Notification createLetterNotificationWith(final UUID notificationId,
                                                      final String status,
                                                      final String letterUrl,
                                                      final ZonedDateTime dateCreated,
                                                      final ZonedDateTime lastUpdated) {
        return new NotificationFactory().createLetterNotification(notificationId, status, letterUrl, dateCreated, lastUpdated);
    }
}