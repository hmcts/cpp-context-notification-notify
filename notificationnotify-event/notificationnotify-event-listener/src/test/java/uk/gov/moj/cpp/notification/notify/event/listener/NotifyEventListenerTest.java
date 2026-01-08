package uk.gov.moj.cpp.notification.notify.event.listener;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.json.schemas.domains.notificationnotify.FirstClassLetterQueued.firstClassLetterQueued;
import static uk.gov.justice.json.schemas.domains.notificationnotify.LetterQueued.letterQueued;
import static uk.gov.justice.json.schemas.domains.notificationnotify.LetterQueuedForResend.letterQueuedForResend;
import static uk.gov.justice.json.schemas.domains.notificationnotify.NotificationAttempted.notificationAttempted;
import static uk.gov.justice.json.schemas.domains.notificationnotify.NotificationFailed.notificationFailed;
import static uk.gov.justice.json.schemas.domains.notificationnotify.NotificationQueued.notificationQueued;
import static uk.gov.justice.json.schemas.domains.notificationnotify.NotificationSent.notificationSent;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.test.utils.core.messaging.MetadataBuilderFactory.metadataWithDefaults;
import static uk.gov.moj.cpp.notification.entity.NotificationType.EMAIL;
import static uk.gov.moj.cpp.notification.entity.NotificationType.LETTER;
import static uk.gov.moj.cpp.notification.notify.event.listener.NotificationStatus.FAILED;
import static uk.gov.moj.cpp.notification.notify.event.listener.NotificationStatus.SENT;
import static uk.gov.moj.cpp.notification.notify.event.listener.NotificationStatus.URL_FAILED;
import static uk.gov.moj.cpp.notification.notify.event.listener.NotificationStatus.VALIDATION_FAILED;

import uk.gov.justice.json.schemas.domains.notificationnotify.FirstClassLetterQueued;
import uk.gov.justice.json.schemas.domains.notificationnotify.LetterQueued;
import uk.gov.justice.json.schemas.domains.notificationnotify.LetterQueuedForResend;
import uk.gov.justice.json.schemas.domains.notificationnotify.NotificationAttempted;
import uk.gov.justice.json.schemas.domains.notificationnotify.NotificationFailed;
import uk.gov.justice.json.schemas.domains.notificationnotify.NotificationQueued;
import uk.gov.justice.json.schemas.domains.notificationnotify.NotificationSent;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.notification.entity.Notification;
import uk.gov.moj.cpp.notification.factory.NotificationFactory;
import uk.gov.moj.cpp.notification.repository.NotificationRepository;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class NotifyEventListenerTest {

    private static final UUID NOTIFICATION_ID = randomUUID();

    @Mock
    private UtcClock clock;

    @Mock
    private NotificationRepository notificationRepository;

    @Spy
    private NotificationFactory notificationFactory;

    @InjectMocks
    private NotifyEventListener notifyEventListener;

    private final ArgumentCaptor<Notification> notificationArgumentCaptor = ArgumentCaptor.forClass(Notification.class);

    @Test
    public void shouldStoreEmailNotificationInDatabase() {
        final ZonedDateTime timestamp = new UtcClock().now();
        final NotificationQueued notificationQueued = notificationQueued()
                .withNotificationId(NOTIFICATION_ID)
                .withSendToAddress("person@organisation.com")
                .build();

        final Envelope<NotificationQueued> notificationQueuedEvent = envelopeFrom(metadataWithDefaults(), notificationQueued);

        when(clock.now()).thenReturn(timestamp);

        notifyEventListener.notificationQueued(notificationQueuedEvent);

        verify(notificationRepository).save(notificationArgumentCaptor.capture());

        final Notification notification = notificationArgumentCaptor.getValue();
        assertThat(notification.getNotificationId(), is(NOTIFICATION_ID));
        assertThat(notification.getNotificationType(), is(EMAIL.name()));
        assertThat(notification.getSendToAddress(), is("person@organisation.com"));
        assertThat(notification.getDateCreated(), is(timestamp));
        assertThat(notification.getLastUpdated(), is(timestamp));
        assertThat(notification.getLetterUrl(), is(nullValue()));
    }


    @Test
    public void shouldStoreEmailWithEmailUrlNotificationInDatabase() {
        final ZonedDateTime timestamp = new UtcClock().now();
        final String materialUrl = "http://linkToDownload.com";
        final NotificationQueued notificationQueued = notificationQueued()
                .withNotificationId(NOTIFICATION_ID)
                .withMaterialUrl(materialUrl)
                .withSendToAddress("person@organisation.com")
                .build();

        final Envelope<NotificationQueued> notificationQueuedEvent = envelopeFrom(metadataWithDefaults(), notificationQueued);

        when(clock.now()).thenReturn(timestamp);

        notifyEventListener.notificationQueued(notificationQueuedEvent);

        verify(notificationRepository).save(notificationArgumentCaptor.capture());

        final Notification notification = notificationArgumentCaptor.getValue();
        assertThat(notification.getNotificationId(), is(NOTIFICATION_ID));
        assertThat(notification.getNotificationType(), is(EMAIL.name()));
        assertThat(notification.getMaterialUrl(), is(materialUrl));
        assertThat(notification.getSendToAddress(), is("person@organisation.com"));
        assertThat(notification.getDateCreated(), is(timestamp));
        assertThat(notification.getLastUpdated(), is(timestamp));
        assertThat(notification.getLetterUrl(), is(nullValue()));
    }

    @Test
    public void shouldStoreLetterNotificationInDatabase() {
        final ZonedDateTime timestamp = new UtcClock().now();
        final String letterUrl = "http://localhost:8080/some-context/somewhere/letter/90fe9fe3-0e03-40c3-8298-ec6f55323582";
        final LetterQueued letterQueued = letterQueued()
                .withNotificationId(NOTIFICATION_ID)
                .withLetterUrl(letterUrl)
                .build();

        final Envelope<LetterQueued> letterQueuedEvent = envelopeFrom(metadataWithDefaults(), letterQueued);

        when(clock.now()).thenReturn(timestamp);

        notifyEventListener.letterQueued(letterQueuedEvent);

        verify(notificationRepository).save(notificationArgumentCaptor.capture());

        final Notification notification = notificationArgumentCaptor.getValue();
        assertThat(notification.getNotificationId(), is(NOTIFICATION_ID));
        assertThat(notification.getNotificationType(), is(LETTER.name()));
        assertThat(notification.getLetterUrl(), is(letterUrl));
        assertThat(notification.getDateCreated(), is(timestamp));
        assertThat(notification.getLastUpdated(), is(timestamp));
        assertThat(notification.getSendToAddress(), is(nullValue()));
    }

    @Test
    public void shouldStoreLetterNotificationInDatabaseForFirstClassLetter() {
        final ZonedDateTime timestamp = new UtcClock().now();
        final String letterUrl = "http://localhost:8080/some-context/somewhere/letter/90fe9fe3-0e03-40c3-8298-ec6f55323582";
        final FirstClassLetterQueued firstClassLetterQueued = firstClassLetterQueued()
                .withNotificationId(NOTIFICATION_ID)
                .withLetterUrl(letterUrl)
                .build();

        final Envelope<FirstClassLetterQueued> classLetterQueuedEvent = envelopeFrom(metadataWithDefaults(), firstClassLetterQueued);

        when(clock.now()).thenReturn(timestamp);

        notifyEventListener.firstClassLetterQueued(classLetterQueuedEvent);

        verify(notificationRepository).save(notificationArgumentCaptor.capture());

        final Notification notification = notificationArgumentCaptor.getValue();
        assertThat(notification.getNotificationId(), is(NOTIFICATION_ID));
        assertThat(notification.getNotificationType(), is(LETTER.name()));
        assertThat(notification.getLetterUrl(), is(letterUrl));
        assertThat(notification.getDateCreated(), is(timestamp));
        assertThat(notification.getLastUpdated(), is(timestamp));
        assertThat(notification.getSendToAddress(), is(nullValue()));
    }

    @Test
    public void shouldStoreResendLetterNotification() {
        final ZonedDateTime timestamp = new UtcClock().now();
        final String letterUrl = "http://localhost:8080/some-context/somewhere/letter/90fe9fe3-0e03-40c3-8298-ec6f55323582";
        final LetterQueuedForResend letterQueuedForResend = letterQueuedForResend()
                .withNotificationId(NOTIFICATION_ID)
                .withLetterUrl(letterUrl)
                .withReason("validation failed automatic resend attempt, remaining = 5")
                .build();

        final Envelope<LetterQueuedForResend> letterQueuedForResendEvent = envelopeFrom(metadataWithDefaults(), letterQueuedForResend);

        when(clock.now()).thenReturn(timestamp);

        notifyEventListener.letterQueuedForResend(letterQueuedForResendEvent);

        verify(notificationRepository).save(notificationArgumentCaptor.capture());

        final Notification notification = notificationArgumentCaptor.getValue();
        assertThat(notification.getNotificationId(), is(NOTIFICATION_ID));
        assertThat(notification.getNotificationType(), is(LETTER.name()));
        assertThat(notification.getLetterUrl(), is(letterUrl));
        assertThat(notification.getDateCreated(), is(timestamp));
        assertThat(notification.getLastUpdated(), is(timestamp));
        assertThat(notification.getSendToAddress(), is(nullValue()));
    }

    @Test
    public void shouldUpdateNotificationAsAttempted() {
        final ZonedDateTime timestamp = new UtcClock().now();
        final NotificationAttempted notificationAttempted = createNotificationAttempted(timestamp);

        final Envelope<NotificationAttempted> notificationAttemptedEvent = envelopeFrom(metadataWithDefaults(), notificationAttempted);

        when(notificationRepository.findBy(NOTIFICATION_ID)).thenReturn(new Notification());

        notifyEventListener.notificationAttempted(notificationAttemptedEvent);

        verify(notificationRepository).save(notificationArgumentCaptor.capture());

        final Notification notification = notificationArgumentCaptor.getValue();
        assertThat(notification.getErrorMessage(), is("some error"));
        assertThat(notification.getStatusCode(), is(400));
        assertThat(notification.getStatus(), is("ATTEMPTED"));
        assertThat(notification.getLastUpdated(), is(timestamp));
    }

    @Test
    public void shouldNotUpdateNotificationAsAttemptedWhenAlreadyInFailedState() {
        final ZonedDateTime timestamp = new UtcClock().now();
        final NotificationAttempted notificationFailed = createNotificationAttempted(timestamp);

        final Envelope<NotificationAttempted> notificationAttemptedEvent = envelopeFrom(metadataWithDefaults(), notificationFailed);

        when(notificationRepository.findBy(NOTIFICATION_ID)).thenReturn(createNotificationWithStatus(FAILED.name()));

        notifyEventListener.notificationAttempted(notificationAttemptedEvent);

        verify(notificationRepository, times(0)).save(notificationArgumentCaptor.capture());
    }

    @Test
    public void shouldNotUpdateNotificationAsAttemptedWhenAlreadyInFailedURLState() {
        final ZonedDateTime timestamp = new UtcClock().now();
        final NotificationAttempted notificationFailed = createNotificationAttempted(timestamp);

        final Envelope<NotificationAttempted> notificationAttemptedEvent = envelopeFrom(metadataWithDefaults(), notificationFailed);

        when(notificationRepository.findBy(NOTIFICATION_ID)).thenReturn(createNotificationWithStatus(URL_FAILED.name()));

        notifyEventListener.notificationAttempted(notificationAttemptedEvent);

        verify(notificationRepository, times(0)).save(notificationArgumentCaptor.capture());
    }

    @Test
    public void shouldNotUpdateNotificationAsAttemptedWhenAlreadyInValidationFailedState() {
        final ZonedDateTime timestamp = new UtcClock().now();
        final NotificationAttempted notificationFailed = createNotificationAttempted(timestamp);

        final Envelope<NotificationAttempted> notificationAttemptedEvent = envelopeFrom(metadataWithDefaults(), notificationFailed);

        when(notificationRepository.findBy(NOTIFICATION_ID)).thenReturn(createNotificationWithStatus(VALIDATION_FAILED.name()));

        notifyEventListener.notificationAttempted(notificationAttemptedEvent);

        verify(notificationRepository, times(0)).save(notificationArgumentCaptor.capture());
    }

    @Test
    public void shouldNotUpdateNotificationAsAttemptedWhenAlreadyInSentState() {
        final ZonedDateTime timestamp = new UtcClock().now();
        final NotificationAttempted notificationFailed = createNotificationAttempted(timestamp);

        final Envelope<NotificationAttempted> notificationAttemptedEvent = envelopeFrom(metadataWithDefaults(), notificationFailed);

        when(notificationRepository.findBy(NOTIFICATION_ID)).thenReturn(createNotificationWithStatus(SENT.name()));

        notifyEventListener.notificationAttempted(notificationAttemptedEvent);

        verify(notificationRepository, times(0)).save(notificationArgumentCaptor.capture());
    }


    @Test
    public void shouldUpdateNotificationAsFailed() {
        final ZonedDateTime timestamp = new UtcClock().now();
        final NotificationFailed notificationFailed = notificationFailed()
                .withNotificationId(NOTIFICATION_ID)
                .withFailedTime(timestamp)
                .withErrorMessage("some error")
                .withStatusCode(400)
                .build();

        final Envelope<NotificationFailed> notificationFailedEvent = envelopeFrom(metadataWithDefaults(), notificationFailed);

        when(notificationRepository.findBy(NOTIFICATION_ID)).thenReturn(new Notification());

        notifyEventListener.notificationFailed(notificationFailedEvent);

        verify(notificationRepository).save(notificationArgumentCaptor.capture());

        final Notification notification = notificationArgumentCaptor.getValue();
        assertThat(notification.getErrorMessage(), is("some error"));
        assertThat(notification.getStatusCode(), is(400));
        assertThat(notification.getStatus(), is("FAILED"));
        assertThat(notification.getLastUpdated(), is(timestamp));
    }

    @Test
    public void shouldUpdateNotificationWithoutStatusCodeAsFailed() {
        final ZonedDateTime timestamp = new UtcClock().now();
        final NotificationFailed notificationFailed = notificationFailed()
                .withNotificationId(NOTIFICATION_ID)
                .withFailedTime(timestamp)
                .withErrorMessage("some error")
                .build();

        final Envelope<NotificationFailed> notificationFailedEvent = envelopeFrom(metadataWithDefaults(), notificationFailed);

        when(notificationRepository.findBy(NOTIFICATION_ID)).thenReturn(new Notification());

        notifyEventListener.notificationFailed(notificationFailedEvent);

        verify(notificationRepository).save(notificationArgumentCaptor.capture());

        final Notification notification = notificationArgumentCaptor.getValue();
        assertThat(notification.getErrorMessage(), is("some error"));
        assertThat(notification.getStatus(), is("FAILED"));
        assertThat(notification.getLastUpdated(), is(timestamp));
        assertThat(notification.getStatusCode(), is(nullValue()));
    }

    @Test
    public void shouldUpdateNotificationAsSent() {
        final ZonedDateTime timestamp = new UtcClock().now();
        final NotificationSent notificationSent = notificationSent()
                .withNotificationId(NOTIFICATION_ID)
                .withSentTime(timestamp)
                .build();

        final Envelope<NotificationSent> notificationSentEvent = envelopeFrom(metadataWithDefaults(), notificationSent);

        when(notificationRepository.findBy(NOTIFICATION_ID)).thenReturn(new Notification());

        notifyEventListener.notificationSent(notificationSentEvent);

        verify(notificationRepository).save(notificationArgumentCaptor.capture());

        final Notification notification = notificationArgumentCaptor.getValue();
        assertThat(notification.getStatus(), is("SENT"));
        assertThat(notification.getLastUpdated(), is(timestamp));
    }

    private NotificationAttempted createNotificationAttempted(final ZonedDateTime timestamp) {
        return notificationAttempted()
                .withNotificationId(NOTIFICATION_ID)
                .withAttemptedTime(timestamp)
                .withErrorMessage("some error")
                .withStatusCode(400)
                .build();
    }

    private Notification createNotificationWithStatus(final String status) {
        final Notification notification = new Notification(NOTIFICATION_ID, LETTER);
        notification.setStatus(status);

        return notification;
    }
}
