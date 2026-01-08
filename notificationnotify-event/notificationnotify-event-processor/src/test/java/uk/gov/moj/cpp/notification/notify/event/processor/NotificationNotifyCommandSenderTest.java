package uk.gov.moj.cpp.notification.notify.event.processor;

import static com.jayway.jsonassert.JsonAssert.with;
import static java.time.ZoneOffset.UTC;
import static java.time.ZonedDateTime.of;
import static java.util.Optional.of;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.justice.services.common.converter.ZonedDateTimes;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.NotificationEmailDetails;

import java.time.ZonedDateTime;
import java.util.UUID;

import javax.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class NotificationNotifyCommandSenderTest {

    private static final ZonedDateTime now = of(2020, 6, 21, 11, 23, 23, 0, UTC);
    private static final UUID notificationId = UUID.randomUUID();
    private static final String MAIL_SERVER = "outlook.office365.com";

    private static final String MARK_AS_SENT = "notificationnotify.command.mark-as-sent";
    private static final String MARK_AS_FAILED = "notificationnotify.command.mark-as-failed";
    private static final String MARK_AS_ATTEMPTED = "notificationnotify.command.mark-as-attempted";
    private static final String MARK_AS_INVALID = "notificationnotify.command.mark-as-invalid";

    private static final String NOTIFICATIONNOTIFY_COMMAND_PROCESS_BOUNCED_EMAIL = "notificationnotify.command.process-bounced-email";
    private static final String NOTIFICATIONNOTIFY_COMMAND_RECORD_CHECK_BOUNCED_EMAIL_REQUEST_FAILED = "notificationnotify.command.record-check-bounced-email-request-failed";


    @Mock
    private Sender sender;

    @Mock
    private UtcClock utcClock;

    @InjectMocks
    private NotificationNotifyCommandSender notificationNotifyCommandSender;

    @Captor
    private ArgumentCaptor<Envelope> envelopeArgumentCaptor;

    @Test
    public void markNotificationSent() {

        when(utcClock.now()).thenReturn(now);

        notificationNotifyCommandSender.markAsSent(notificationId, NotificationEmailDetails.emailDetails()
                .build());

        verify(sender).send(envelopeArgumentCaptor.capture());

        final Envelope envelope = envelopeArgumentCaptor.getValue();

        final Metadata metadata = envelope.metadata();
        assertThat(metadata.id(), is(notNullValue()));
        assertThat(metadata.streamId().get().toString(), is(notificationId.toString()));
        assertThat(metadata.name(), is(MARK_AS_SENT));
        assertThat(metadata.createdAt().get(), is(now));

        final JsonObject payload = (JsonObject) envelope.payload();
        with(payload.toString())
                .assertThat("notificationId", is(notificationId.toString()))
                .assertThat("sentTime", is(ZonedDateTimes.toString(now)));
    }

    @Test
    public void markNotificationFailed() {

        final String errorMessage = "error";
        final int statusCode = SC_NOT_FOUND;

        when(utcClock.now()).thenReturn(now);

        notificationNotifyCommandSender.markNotificationFailed(notificationId, errorMessage, of(statusCode));

        verify(sender).send(envelopeArgumentCaptor.capture());

        final Envelope envelope = envelopeArgumentCaptor.getValue();

        final Metadata metadata = envelope.metadata();
        assertThat(metadata.id(), is(notNullValue()));
        assertThat(metadata.streamId().get().toString(), is(notificationId.toString()));
        assertThat(metadata.name(), is(MARK_AS_FAILED));
        assertThat(metadata.createdAt().get(), is(now));

        final JsonObject payload = (JsonObject) envelope.payload();
        with(payload.toString())
                .assertThat("notificationId", is(notificationId.toString()))
                .assertThat("failedTime", is(ZonedDateTimes.toString(now)))
                .assertThat("errorMessage", is(errorMessage))
                .assertThat("statusCode", is(statusCode));
    }

    @Test
    public void markNotificationAttempted() {

        final String errorMessage = "error";
        final int statusCode = SC_INTERNAL_SERVER_ERROR;

        when(utcClock.now()).thenReturn(now);

        notificationNotifyCommandSender.markAsAttempted(notificationId, errorMessage, statusCode);

        verify(sender).send(envelopeArgumentCaptor.capture());

        final Envelope envelope = envelopeArgumentCaptor.getValue();

        final Metadata metadata = envelope.metadata();
        assertThat(metadata.id(), is(notNullValue()));
        assertThat(metadata.streamId().get().toString(), is(notificationId.toString()));
        assertThat(metadata.name(), is(MARK_AS_ATTEMPTED));
        assertThat(metadata.createdAt().get(), is(now));

        final JsonObject payload = (JsonObject) envelope.payload();
        with(payload.toString())
                .assertThat("notificationId", is(notificationId.toString()))
                .assertThat("attemptedTime", is(ZonedDateTimes.toString(now)))
                .assertThat("errorMessage", is(errorMessage))
                .assertThat("statusCode", is(statusCode));
    }

    @Test
    public void markAsInvalid() {

        final String errorMessage = "Validation failed";

        when(utcClock.now()).thenReturn(now);

        notificationNotifyCommandSender.markAsInvalid(notificationId, errorMessage, now);

        verify(sender).send(envelopeArgumentCaptor.capture());

        final Envelope envelope = envelopeArgumentCaptor.getValue();

        final Metadata metadata = envelope.metadata();
        assertThat(metadata.id(), is(notNullValue()));
        assertThat(metadata.streamId().get().toString(), is(notificationId.toString()));
        assertThat(metadata.name(), is(MARK_AS_INVALID));
        assertThat(metadata.createdAt().get(), is(NotificationNotifyCommandSenderTest.now));

        final JsonObject payload = (JsonObject) envelope.payload();
        with(payload.toString())
                .assertThat("notificationId", is(notificationId.toString()))
                .assertThat("errorMessage", is(errorMessage))
                .assertThat("failedTime", is("2020-06-21T11:23:23Z"));
    }

    @Test
    public void shouldProcessBouncedEmail() {

        when(utcClock.now()).thenReturn(now);
        notificationNotifyCommandSender.processBouncedEmail(notificationId);

        verify(sender).send(envelopeArgumentCaptor.capture());
        final Envelope envelope = envelopeArgumentCaptor.getValue();

        final Metadata metadata = envelope.metadata();
        assertThat(metadata.id(), is(notNullValue()));
        assertThat(metadata.streamId().get().toString(), is(notificationId.toString()));
        assertThat(metadata.name(), is(NOTIFICATIONNOTIFY_COMMAND_PROCESS_BOUNCED_EMAIL));
        assertThat(metadata.createdAt().get(), is(now));

        final JsonObject payload = (JsonObject) envelope.payload();
        with(payload.toString())
                .assertThat("notificationId", is(notificationId.toString()));
    }

    @Test
    public void shouldRecordCheckBouncedEmailRequestAsFailed() {

        when(utcClock.now()).thenReturn(now);
        notificationNotifyCommandSender.recordCheckBouncedEmailRequestAsFailed(MAIL_SERVER, "server down");

        verify(sender).send(envelopeArgumentCaptor.capture());
        final Envelope envelope = envelopeArgumentCaptor.getValue();

        final Metadata metadata = envelope.metadata();
        assertThat(metadata.id(), is(notNullValue()));
        assertThat(metadata.name(), is(NOTIFICATIONNOTIFY_COMMAND_RECORD_CHECK_BOUNCED_EMAIL_REQUEST_FAILED));
        assertThat(metadata.createdAt().get(), is(now));

        final JsonObject payload = (JsonObject) envelope.payload();
        with(payload.toString())
                .assertThat("server", is(MAIL_SERVER))
                .assertThat("reason", is("server down"));
    }
}
