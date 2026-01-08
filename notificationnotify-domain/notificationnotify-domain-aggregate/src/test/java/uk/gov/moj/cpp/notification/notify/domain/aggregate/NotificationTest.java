package uk.gov.moj.cpp.notification.notify.domain.aggregate;

import static java.lang.String.format;
import static java.time.ZonedDateTime.now;
import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;

import uk.gov.justice.json.schemas.domains.notificationnotify.BouncedEmailAlreadyNotified;
import uk.gov.justice.json.schemas.domains.notificationnotify.EmailNotificationBounced;
import uk.gov.justice.json.schemas.domains.notificationnotify.FirstClassLetterQueued;
import uk.gov.justice.json.schemas.domains.notificationnotify.LetterQueued;
import uk.gov.justice.json.schemas.domains.notificationnotify.LetterQueuedForResend;
import uk.gov.justice.json.schemas.domains.notificationnotify.NotificationAttempted;
import uk.gov.justice.json.schemas.domains.notificationnotify.NotificationFailed;
import uk.gov.justice.json.schemas.domains.notificationnotify.NotificationQueued;
import uk.gov.justice.json.schemas.domains.notificationnotify.NotificationSent;
import uk.gov.justice.json.schemas.domains.notificationnotify.Personalisation;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public class NotificationTest {

    private static final UUID notificationId = randomUUID();

    private final static String ERROR_MESSAGE = "error message";
    private final static String CLIENT_CONTEXT = "correspondence";

    @Test
    public void shouldSendEmailWithMaterialUrl() {

        final UUID templateId = randomUUID();
        final String sendToAddress = "sendToAddress";
        final Optional<String> replyToAddress = of("replyToAddress");
        final Optional<String> clientContext = of("correspondence");
        final Optional<UUID> replyToAddressId = of(randomUUID());
        final Optional<String> materialUrl = of("http://localhost:8080/material-query-api/query/api/rest/material/b439f425-e894-4a2c-aeb8-ed172565720f");
        final Personalisation personalisation = Personalisation.personalisation().withAdditionalProperty("name", "value").build();

        final Notification notification = new Notification();
        final Stream<Object> events = notification.sendWithMaterialAttachment(notificationId, templateId, sendToAddress, replyToAddress, replyToAddressId, materialUrl, of(personalisation), clientContext);
        final List eventsList = events.collect(toList());

        final Object event = eventsList.get(0);
        final NotificationQueued notificationQueued = (NotificationQueued) event;

        assertThat(eventsList.size(), Matchers.is(1));
        assertEquals(NotificationQueued.class, event.getClass());
        assertThat(notificationQueued.getNotificationId(), is(notificationId));
        assertThat(notificationQueued.getTemplateId(), is(templateId));
        assertThat(notificationQueued.getSendToAddress(), is(sendToAddress));
        assertThat(notificationQueued.getReplyToAddress(), is(replyToAddress));
        assertThat(notificationQueued.getPersonalisation(), is(of(personalisation)));
        assertThat(notificationQueued.getMaterialUrl(), is(materialUrl));
        assertThat(notificationQueued.getFileId(), is(Optional.empty()));
    }

    @Test
    public void shouldSendEmailWithFileId() {

        final UUID templateId = randomUUID();
        final String sendToAddress = "sendToAddress";
        final Optional<String> replyToAddress = of("replyToAddress");
        final Optional<UUID> replyToAddressId = of(randomUUID());
        final Optional<UUID> fileId = of(randomUUID());
        final Personalisation personalisation = Personalisation.personalisation().withAdditionalProperty("name", "value").build();
        final Optional<String> clientContext = of(CLIENT_CONTEXT);

        final Notification notification = new Notification();
        final Stream<Object> events = notification.sendWithFileIdAttachment(notificationId, templateId, sendToAddress, replyToAddress, replyToAddressId, fileId, of(personalisation), clientContext);
        final List eventsList = events.collect(toList());

        final Object event = eventsList.get(0);
        final NotificationQueued notificationQueued = (NotificationQueued) event;

        assertThat(eventsList.size(), Matchers.is(1));
        assertEquals(NotificationQueued.class, event.getClass());
        assertThat(notificationQueued.getNotificationId(), is(notificationId));
        assertThat(notificationQueued.getTemplateId(), is(templateId));
        assertThat(notificationQueued.getSendToAddress(), is(sendToAddress));
        assertThat(notificationQueued.getReplyToAddress(), is(replyToAddress));
        assertThat(notificationQueued.getPersonalisation(), is(of(personalisation)));
        assertThat(notificationQueued.getFileId(), is(fileId));
        assertThat(notificationQueued.getMaterialUrl(), is(Optional.empty()));
    }

    @Test
    public void shouldSendEmailWithoutMaterialUrl() {

        final UUID templateId = randomUUID();
        final String sendToAddress = "sendToAddress";
        final Optional<String> replyToAddress = of("replyToAddress");
        final Optional<UUID> replyToAddressId = of(randomUUID());
        final Personalisation personalisation = Personalisation.personalisation().withAdditionalProperty("name", "value").build();
        final Optional<String> clientContext = of(CLIENT_CONTEXT);

        final Notification notification = new Notification();
        final Stream<Object> events = notification.send(notificationId, templateId, sendToAddress, replyToAddress, replyToAddressId, of(personalisation), clientContext);
        final List eventsList = events.collect(toList());

        final Object event = eventsList.get(0);
        final NotificationQueued notificationQueued = (NotificationQueued) event;

        assertThat(eventsList.size(), Matchers.is(1));
        assertEquals(NotificationQueued.class, event.getClass());
        assertThat(notificationQueued.getNotificationId(), is(notificationId));
        assertThat(notificationQueued.getTemplateId(), is(templateId));
        assertThat(notificationQueued.getSendToAddress(), is(sendToAddress));
        assertThat(notificationQueued.getReplyToAddress(), is(replyToAddress));
        assertThat(notificationQueued.getPersonalisation(), is(of(personalisation)));
    }


    @Test
    public void shouldNotSendDuplicateNotification() {
        final UUID templateId = randomUUID();
        final String sendToAddress = "sendToAddress";
        final Optional<String> replyToAddress = of("replyToAddress");
        final Optional<UUID> replyToAddressId = of(randomUUID());
        final Personalisation personalisation = Personalisation.personalisation().withAdditionalProperty("name", "value").build();
        final Optional<String> materialUrl = of("http://localhost:8080/material-query-api/query/api/rest/material/b439f425-e894-4a2c-aeb8-ed172565720f");
        final Optional<String> clientContext = of(CLIENT_CONTEXT);

        final Notification notification = new Notification();

        assertFalse(notification.sendWithMaterialAttachment(notificationId, templateId, sendToAddress, replyToAddress, replyToAddressId, materialUrl, of(personalisation), clientContext).collect(toList()).isEmpty());
        assertTrue(notification.sendWithMaterialAttachment(notificationId, templateId, sendToAddress, replyToAddress, replyToAddressId, materialUrl, of(personalisation), clientContext).collect(toList()).isEmpty());
    }

    @Test
    public void shouldMarkAsSent() {

        final Notification notification = sendEmailNotification(notificationId);

        final ZonedDateTime sentTime = now();
        final Stream<Object> notificationSentEvent = notification.markAsSent(sentTime,
                null,null,null,null,
                null);
        final List eventsList = notificationSentEvent.collect(toList());
        final Object event = eventsList.get(0);
        final NotificationSent notificationSent = (NotificationSent) event;

        assertThat(eventsList.size(), is(1));
        assertEquals(NotificationSent.class, event.getClass());
        assertThat(notificationSent.getNotificationId(), is(notificationId));
        assertThat(notificationSent.getSentTime(), is(sentTime));
    }

    @Test
    public void shouldMarkAsFailed() {

        final Notification notification = sendEmailNotification(notificationId);

        final ZonedDateTime failedTime = now();
        final Optional<Integer> statusCode = of(429);

        final Stream<Object> notificationFailedEvent =
                notification.markAsFailed(failedTime, ERROR_MESSAGE, statusCode);
        final List eventsList = notificationFailedEvent.collect(toList());
        final Object event = eventsList.get(0);
        final NotificationFailed notificationFailed = (NotificationFailed) event;

        assertThat(eventsList.size(), is(1));
        assertEquals(NotificationFailed.class, event.getClass());
        assertThat(notificationFailed.getNotificationId(), is(notificationId));
        assertThat(notificationFailed.getFailedTime(), is(failedTime));
        assertThat(notificationFailed.getStatusCode(), is(statusCode));
        assertThat(notificationFailed.getErrorMessage(), is(ERROR_MESSAGE));
    }

    @Test
    public void shouldMarkAsAttempted() {

        final Notification notification = sendEmailNotification(notificationId);

        final ZonedDateTime attemptedTime = now();
        final Integer statusCode = 429;


        final Stream<Object> NotificationAttemptedEvent = notification.markAsAttempted(attemptedTime, ERROR_MESSAGE, statusCode);
        final List eventsList = NotificationAttemptedEvent.collect(toList());
        final Object event = eventsList.get(0);
        final NotificationAttempted notificationAttempted = (NotificationAttempted) event;

        assertThat(eventsList.size(), is(1));
        assertEquals(NotificationAttempted.class, event.getClass());
        assertThat(notificationAttempted.getNotificationId(), is(notificationId));
        assertThat(notificationAttempted.getAttemptedTime(), is(attemptedTime));
        assertThat(notificationAttempted.getStatusCode(), is(statusCode));
        assertThat(notificationAttempted.getErrorMessage(), is(ERROR_MESSAGE));
    }

    @Test
    public void shouldSendStandardLetterWhenNotFirstClass() throws Exception {

        final UUID notificationId = randomUUID();
        final String letterUrl = "http://letter/download/url";

        final Notification notification = new Notification();
        final Stream<Object> stream = notification.send(notificationId, letterUrl, Optional.of("second"), Optional.of(CLIENT_CONTEXT));

        final List<Object> events = stream.collect(toList());

        assertThat(events.size(), is(1));
        final Object event = events.get(0);
        assertEquals(LetterQueued.class, event.getClass());
        final LetterQueued letterQueued = (LetterQueued) event;
        assertThat(letterQueued.getNotificationId(), is(notificationId));
        assertThat(letterQueued.getLetterUrl(), is(letterUrl));
    }

    @Test
    public void shouldSendStandardLetterWhenNoPostageSpecified() throws Exception {

        final UUID notificationId = randomUUID();
        final String letterUrl = "http://letter/download/url";

        final Notification notification = new Notification();
        final Stream<Object> stream = notification.send(notificationId, letterUrl, Optional.empty(), Optional.of(CLIENT_CONTEXT));

        final List<Object> events = stream.collect(toList());

        assertThat(events.size(), is(1));
        final Object event = events.get(0);
        assertEquals(LetterQueued.class, event.getClass());
        final LetterQueued letterQueued = (LetterQueued) event;
        assertThat(letterQueued.getNotificationId(), is(notificationId));
        assertThat(letterQueued.getLetterUrl(), is(letterUrl));
    }

    @Test
    public void shouldSendLetterFirstClassLetter() throws Exception {

        final UUID notificationId = randomUUID();
        final String letterUrl = "http://letter/download/url";

        final Notification notification = new Notification();
        final Stream<Object> stream = notification.send(notificationId, letterUrl, Optional.of("first"), Optional.of(CLIENT_CONTEXT));

        final List<Object> events = stream.collect(toList());

        assertThat(events.size(), is(1));
        final Object event = events.get(0);
        assertEquals(FirstClassLetterQueued.class, event.getClass());
        final FirstClassLetterQueued letterQueued = (FirstClassLetterQueued) event;
        assertThat(letterQueued.getNotificationId(), is(notificationId));
        assertThat(letterQueued.getLetterUrl(), is(letterUrl));
    }

    @Test
    public void shouldGenerateResendLetterEvent() throws Exception {
        final ZonedDateTime failedTime = now();

        final UUID notificationId = randomUUID();
        final String letterUrl = "http://letter/download/url";

        final Notification notification = new Notification();

        setField(notification, "letterUrl", letterUrl);
        final String errorMessage = format("Validation failed for %s with status '%s' ", notificationId, 400);

        final Stream<Object> stream = notification.markAsInvalid(notificationId, errorMessage, failedTime);

        final List<Object> events = stream.collect(toList());

        assertThat(events.size(), is(1));

        final LetterQueuedForResend letterQueuedForResend = (LetterQueuedForResend) events.get(0);
        assertThat(letterQueuedForResend.getNotificationId(), is(notificationId));
        assertThat(letterQueuedForResend.getLetterUrl(), is(letterUrl));
        assertThat(letterQueuedForResend.getReason(), is(format("%s %s", errorMessage, "automatic resend attempt, remaining = 5")));
    }

    @Test
    public void shouldGenerateResendFirstClassLetterEvent() throws Exception {
        final UUID notificationId = randomUUID();
        final String letterUrl = "http://letter/download/url";

        final Notification notification = new Notification();
        notification.send(notificationId, letterUrl, Optional.of("first"), Optional.of(CLIENT_CONTEXT));

        final ZonedDateTime failedTime = now();

        final String errorMessage = format("Validation failed for %s with status '%s' ", notificationId, 400);

        final Stream<Object> stream = notification.markAsInvalid(notificationId, errorMessage, failedTime);

        final List<Object> events = stream.collect(toList());

        assertThat(events.size(), is(1));

        final LetterQueuedForResend letterQueuedForResend = (LetterQueuedForResend) events.get(0);
        assertThat(letterQueuedForResend.getNotificationId(), is(notificationId));
        assertThat(letterQueuedForResend.getLetterUrl(), is(letterUrl));
        assertThat(letterQueuedForResend.getReason(), is(format("%s %s", errorMessage, "automatic resend attempt, remaining = 5")));
        assertThat(letterQueuedForResend.getPostage().get(), is("first"));
    }

    @Test
    public void shouldNotGenerateResendLetterEvent() throws Exception {

        final UUID notificationId = randomUUID();
        final Optional<Integer> statusCode = of(400);
        final Notification notification = new Notification();
        setField(notification, "resendAttemptsRemaining", 0);
        final ZonedDateTime failedTime = now();
        final String errorMessage = format("Validation failed for '%s' with status '%s' ", notificationId, 400);
        final Stream<Object> stream = notification.markAsInvalid(notificationId, errorMessage, failedTime);

        final List<Object> events = stream.collect(toList());
        assertThat(events.size(), is(1));
        final Object event = events.get(0);

        final NotificationFailed notificationFailed = ((NotificationFailed) events.get(0));

        assertEquals(NotificationFailed.class, event.getClass());
        assertThat(notificationFailed.getNotificationId(), is(notificationId));
        assertThat(notificationFailed.getStatusCode(), is(statusCode));
        assertThat(notificationFailed.getFailedTime(), is(failedTime));
        assertThat(notificationFailed.getErrorMessage(), is(format("%s %s", errorMessage, "automatic resend attempt, remaining = 0")));
    }

    @Test
    public void shouldNotSendDuplicateLetterNotification() {
        final UUID notificationId = randomUUID();
        final String letterUrl = "http://letter/download/url";

        final Notification notification = new Notification();
        final Stream<Object> stream = notification.send(notificationId, letterUrl, Optional.of("first"), Optional.of(CLIENT_CONTEXT));

        assertThat(stream.count(), is(1L));

        final Stream<Object> secondStream = notification.send(notificationId, letterUrl, Optional.of("first"), Optional.of(CLIENT_CONTEXT));

        assertThat(secondStream.count(), is(0L));
    }

    @Test
    public void shouldProcessBouncedNotification() {

        final Notification notification = sendEmailNotification(notificationId);
        final Stream<Object> notificationBouncedEvent = notification.processBouncedEmail();
        final List eventsList = notificationBouncedEvent.collect(toList());
        final Object event = eventsList.get(0);
        assertThat(eventsList.size(), is(1));
        assertEquals(EmailNotificationBounced.class, event.getClass());
        final EmailNotificationBounced notificationBounced = (EmailNotificationBounced) event;
        assertThat(notificationBounced.getNotificationId(), is(notificationId));
    }

    @Test
    public void shouldRaiseAlreadyBouncedEmailNotifiedEventWhenBouncedEmailIsAlreadyProcessed() {

        final Notification notification = sendEmailNotification(notificationId);
        notification.processBouncedEmail();
        final Stream<Object> notificationBouncedEvent = notification.processBouncedEmail();
        final List eventsList = notificationBouncedEvent.collect(toList());
        final Object event = eventsList.get(0);
        assertThat(eventsList.size(), is(1));
        assertEquals(BouncedEmailAlreadyNotified.class, event.getClass());
        final BouncedEmailAlreadyNotified bouncedEmailAlreadyNotified = (BouncedEmailAlreadyNotified) event;
        assertThat(bouncedEmailAlreadyNotified.getNotificationId(), is(notificationId));
    }


    private Notification sendEmailNotification(final UUID notificationId) {
        final UUID templateId = randomUUID();
        final String sendToAddress = "sendToAddress";
        final Optional<String> replyToAddress = of("replyToAddress");
        final Optional<UUID> replyToAddressId = of(randomUUID());
        final Optional<String> clientContext = of(CLIENT_CONTEXT);
        final Optional<String> linkToDownload = of("http://localhost:8080/material-query-api/query/api/rest/material/b439f425-e894-4a2c-aeb8-ed172565720f");
        final Personalisation personalisation = Personalisation.personalisation().withAdditionalProperty("name", "value").build();
        final Notification notification = new Notification();
        notification.sendWithMaterialAttachment(notificationId, templateId, sendToAddress, replyToAddress, replyToAddressId, linkToDownload, of(personalisation),clientContext);
        return notification;
    }
}
