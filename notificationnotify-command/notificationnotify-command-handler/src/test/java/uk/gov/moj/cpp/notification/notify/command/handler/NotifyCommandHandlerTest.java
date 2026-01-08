package uk.gov.moj.cpp.notification.notify.command.handler;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.withJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.withoutJsonPath;
import static java.time.ZonedDateTime.now;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static java.util.UUID.randomUUID;
import static java.util.stream.Stream.of;
import static javax.json.JsonValue.NULL;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.json.schemas.domains.notificationnotify.LetterQueued.letterQueued;
import static uk.gov.justice.json.schemas.domains.notificationnotify.LetterQueuedForResend.letterQueuedForResend;
import static uk.gov.justice.json.schemas.domains.notificationnotify.MarkAsAttempted.markAsAttempted;
import static uk.gov.justice.json.schemas.domains.notificationnotify.MarkAsSent.markAsSent;
import static uk.gov.justice.json.schemas.domains.notificationnotify.NotificationQueued.notificationQueued;
import static uk.gov.justice.json.schemas.domains.notificationnotify.ProcessBouncedEmail.processBouncedEmail;
import static uk.gov.justice.json.schemas.domains.notificationnotify.SendEmailNotification.sendEmailNotification;
import static uk.gov.justice.json.schemas.domains.notificationnotify.SendLetterNotification.sendLetterNotification;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;
import static uk.gov.justice.services.test.utils.core.enveloper.EnveloperFactory.createEnveloperWithEvents;
import static uk.gov.justice.services.test.utils.core.matchers.EventStreamMatcher.eventStreamAppendedWith;
import static uk.gov.justice.services.test.utils.core.matchers.JsonEnvelopeMatcher.jsonEnvelope;
import static uk.gov.justice.services.test.utils.core.matchers.JsonEnvelopeMetadataMatcher.withMetadataEnvelopedFrom;
import static uk.gov.justice.services.test.utils.core.matchers.JsonEnvelopePayloadMatcher.payloadIsJson;
import static uk.gov.justice.services.test.utils.core.matchers.JsonEnvelopeStreamMatcher.streamContaining;

import uk.gov.justice.json.schemas.domains.notificationnotify.CheckBouncedEmailRequestFailed;
import uk.gov.justice.json.schemas.domains.notificationnotify.CheckPocaEmailRequestFailed;
import uk.gov.justice.json.schemas.domains.notificationnotify.EmailNotificationBounced;
import uk.gov.justice.json.schemas.domains.notificationnotify.LetterQueued;
import uk.gov.justice.json.schemas.domains.notificationnotify.LetterQueuedForResend;
import uk.gov.justice.json.schemas.domains.notificationnotify.MarkAsAttempted;
import uk.gov.justice.json.schemas.domains.notificationnotify.MarkAsFailed;
import uk.gov.justice.json.schemas.domains.notificationnotify.MarkAsInvalid;
import uk.gov.justice.json.schemas.domains.notificationnotify.MarkAsSent;
import uk.gov.justice.json.schemas.domains.notificationnotify.NotificationAttempted;
import uk.gov.justice.json.schemas.domains.notificationnotify.NotificationFailed;
import uk.gov.justice.json.schemas.domains.notificationnotify.NotificationQueued;
import uk.gov.justice.json.schemas.domains.notificationnotify.NotificationSent;
import uk.gov.justice.json.schemas.domains.notificationnotify.Personalisation;
import uk.gov.justice.json.schemas.domains.notificationnotify.ProcessBouncedEmail;
import uk.gov.justice.json.schemas.domains.notificationnotify.RecordCheckBouncedEmailRequestFailed;
import uk.gov.justice.json.schemas.domains.notificationnotify.RecordCheckPocaEmailRequestFailed;
import uk.gov.justice.json.schemas.domains.notificationnotify.SendEmailNotification;
import uk.gov.justice.json.schemas.domains.notificationnotify.SendLetterNotification;
import uk.gov.justice.services.common.converter.ZonedDateTimes;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.core.aggregate.AggregateService;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.eventsourcing.source.core.EventSource;
import uk.gov.justice.services.eventsourcing.source.core.EventStream;
import uk.gov.justice.services.eventsourcing.source.core.exception.EventStreamException;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.notification.notify.domain.aggregate.Notification;
import uk.gov.moj.cpp.notification.notify.domain.aggregate.NotificationMonitor;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
public class NotifyCommandHandlerTest {

    private final static String ERROR_MESSAGE = "error message";
    private final static String CLIENT_CONTEXT = "correspondence";

    @Spy
    private final Enveloper enveloper = createEnveloperWithEvents(
            NotificationQueued.class,
            NotificationSent.class,
            NotificationAttempted.class,
            NotificationFailed.class,
            LetterQueued.class,
            LetterQueuedForResend.class,
            EmailNotificationBounced.class,
            CheckBouncedEmailRequestFailed.class,
            CheckPocaEmailRequestFailed.class);
    @Mock
    Notification aggregate;
    @Mock
    NotificationMonitor notificationMonitor;
    @Mock
    AggregateService aggregateService;
    @Mock
    EventSource eventSource;
    @Mock
    EventStream eventStream;
    @InjectMocks
    NotifyCommandHandler notifyCommandHandler;
    @Mock
    Logger logger;

    @Test
    public void shouldHandleSendEmailCommandWithoutFileLink() throws EventStreamException {

        final UUID notificationId = randomUUID();
        final UUID templateId = randomUUID();
        final String sendToAddress = "send";
        final String replyToAddress = "reply";
        final UUID replyToAddressId = randomUUID();
        final Personalisation personalisation = new Personalisation(new HashMap<>());
        personalisation.setAdditionalProperty("name", "value");

        final SendEmailNotification sendEmailNotification = sendEmailNotification()
                .withNotificationId(notificationId)
                .withTemplateId(templateId)
                .withSendToAddress(sendToAddress)
                .withReplyToAddress(replyToAddress)
                .withReplyToAddressId(replyToAddressId)
                .withPersonalisation(personalisation)
                .withClientContext(CLIENT_CONTEXT)
                .build();

        final NotificationQueued notificationQueued = notificationQueued()
                .withNotificationId(notificationId)
                .withSendToAddress(sendToAddress)
                .withReplyToAddress(replyToAddress)
                .withReplyToAddressId(replyToAddressId)
                .withTemplateId(templateId)
                .withPersonalisation(personalisation)
                .build();

        final Envelope<SendEmailNotification> sendEmailNotificationEnvelope = envelopeFrom(metadataBuilder().withId(randomUUID())
                .withName("notificationnotify.command.send-email-notification"), sendEmailNotification);

        when(aggregateService.get(eventStream, Notification.class)).thenReturn(aggregate);
        when(eventSource.getStreamById(notificationId)).thenReturn(eventStream);
        when(aggregate.send(notificationId, templateId, sendToAddress, Optional.of(replyToAddress), Optional.of(replyToAddressId), Optional.of(personalisation), Optional.of(CLIENT_CONTEXT))).thenReturn(of(notificationQueued));

        notifyCommandHandler.sendEmail(sendEmailNotificationEnvelope);

        verify(aggregateService).get(eventStream, Notification.class);

        assertThat(eventStream, eventStreamAppendedWith(streamContaining(
                jsonEnvelope()
                        .withMetadataOf(withMetadataEnvelopedFrom(envelopeFrom(sendEmailNotificationEnvelope.metadata(), NULL))
                                .withName("notificationnotify.events.notification-queued"))
                        .withPayloadOf(
                                payloadIsJson(allOf(
                                        withJsonPath("$.notificationId", is(notificationId.toString())),
                                        withJsonPath("$.templateId", is(templateId.toString())),
                                        withJsonPath("$.sendToAddress", is(sendToAddress)),
                                        withJsonPath("$.replyToAddress", is(replyToAddress)),
                                        withJsonPath("$.personalisation", is(personalisation.getAdditionalProperties())),
                                        withoutJsonPath("$.materialUrl")
                                ))
                        ))));
    }

    @Test
    public void shouldHandleSendEmailCommandWithMaterialFileLink() throws EventStreamException {

        final UUID notificationId = randomUUID();
        final UUID templateId = randomUUID();
        final String sendToAddress = "send";
        final String replyToAddress = "reply";
        final UUID replyToAddressId = randomUUID();
        final Personalisation personalisation = new Personalisation(new HashMap<>());
        final String materialUrl = "http://localhost:8080/material-query-api/query/api/rest/material/b439f425-e894-4a2c-aeb8-ed172565720f";

        personalisation.setAdditionalProperty("name", "value");

        final SendEmailNotification sendEmailNotification = sendEmailNotification()
                .withNotificationId(notificationId)
                .withTemplateId(templateId)
                .withSendToAddress(sendToAddress)
                .withReplyToAddress(replyToAddress)
                .withReplyToAddressId(replyToAddressId)
                .withPersonalisation(personalisation)
                .withMaterialUrl(materialUrl)
                .withClientContext(CLIENT_CONTEXT)
                .build();

        final NotificationQueued notificationQueued = notificationQueued()
                .withNotificationId(notificationId)
                .withSendToAddress(sendToAddress)
                .withReplyToAddress(replyToAddress)
                .withReplyToAddressId(replyToAddressId)
                .withTemplateId(templateId)
                .withPersonalisation(personalisation)
                .withMaterialUrl(materialUrl)
                .build();

        final Envelope<SendEmailNotification> sendEmailNotificationEnvelope = envelopeFrom(metadataBuilder().withId(randomUUID())
                .withName("notificationnotify.command.send-email-notification"), sendEmailNotification);

        when(aggregateService.get(eventStream, Notification.class)).thenReturn(aggregate);
        when(eventSource.getStreamById(notificationId)).thenReturn(eventStream);
        when(aggregate.sendWithMaterialAttachment(notificationId, templateId, sendToAddress, Optional.of(replyToAddress), Optional.of(replyToAddressId), Optional.of(materialUrl), Optional.of(personalisation), Optional.of(CLIENT_CONTEXT))).thenReturn(of(notificationQueued));

        notifyCommandHandler.sendEmail(sendEmailNotificationEnvelope);

        verify(aggregateService).get(eventStream, Notification.class);

        assertThat(eventStream, eventStreamAppendedWith(streamContaining(
                jsonEnvelope()
                        .withMetadataOf(withMetadataEnvelopedFrom(envelopeFrom(sendEmailNotificationEnvelope.metadata(), NULL))
                                .withName("notificationnotify.events.notification-queued"))
                        .withPayloadOf(
                                payloadIsJson(allOf(
                                        withJsonPath("$.notificationId", is(notificationId.toString())),
                                        withJsonPath("$.templateId", is(templateId.toString())),
                                        withJsonPath("$.sendToAddress", is(sendToAddress)),
                                        withJsonPath("$.replyToAddress", is(replyToAddress)),
                                        withJsonPath("$.personalisation", is(personalisation.getAdditionalProperties())),
                                        withJsonPath("$.materialUrl", is(materialUrl))
                                ))
                        ))));
    }

    @Test
    public void shouldHandleSendEmailCommandWithFileServiceFileLink() throws EventStreamException {

        final UUID notificationId = randomUUID();
        final UUID templateId = randomUUID();
        final String sendToAddress = "send";
        final String replyToAddress = "reply";
        final UUID replyToAddressId = randomUUID();
        final Personalisation personalisation = new Personalisation(new HashMap<>());
        final UUID fileId = randomUUID();

        personalisation.setAdditionalProperty("name", "value");

        final SendEmailNotification sendEmailNotification = sendEmailNotification()
                .withNotificationId(notificationId)
                .withTemplateId(templateId)
                .withSendToAddress(sendToAddress)
                .withReplyToAddress(replyToAddress)
                .withReplyToAddressId(replyToAddressId)
                .withPersonalisation(personalisation)
                .withFileId(fileId)
                .withClientContext(CLIENT_CONTEXT)
                .build();

        final NotificationQueued notificationQueued = notificationQueued()
                .withNotificationId(notificationId)
                .withSendToAddress(sendToAddress)
                .withReplyToAddress(replyToAddress)
                .withReplyToAddressId(replyToAddressId)
                .withTemplateId(templateId)
                .withPersonalisation(personalisation)
                .withFileId(fileId)
                .build();

        final Envelope<SendEmailNotification> sendEmailNotificationEnvelope = envelopeFrom(metadataBuilder().withId(randomUUID())
                .withName("notificationnotify.command.send-email-notification"), sendEmailNotification);

        when(aggregateService.get(eventStream, Notification.class)).thenReturn(aggregate);
        when(eventSource.getStreamById(notificationId)).thenReturn(eventStream);
        when(aggregate.sendWithFileIdAttachment(notificationId, templateId, sendToAddress, Optional.of(replyToAddress), Optional.of(replyToAddressId), Optional.of(fileId), Optional.of(personalisation), Optional.of(CLIENT_CONTEXT))).thenReturn(of(notificationQueued));

        notifyCommandHandler.sendEmail(sendEmailNotificationEnvelope);

        verify(aggregateService).get(eventStream, Notification.class);

        assertThat(eventStream, eventStreamAppendedWith(streamContaining(
                jsonEnvelope()
                        .withMetadataOf(withMetadataEnvelopedFrom(envelopeFrom(sendEmailNotificationEnvelope.metadata(), NULL))
                                .withName("notificationnotify.events.notification-queued"))
                        .withPayloadOf(
                                payloadIsJson(allOf(
                                        withJsonPath("$.notificationId", is(notificationId.toString())),
                                        withJsonPath("$.templateId", is(templateId.toString())),
                                        withJsonPath("$.sendToAddress", is(sendToAddress)),
                                        withJsonPath("$.replyToAddress", is(replyToAddress)),
                                        withJsonPath("$.personalisation", is(personalisation.getAdditionalProperties())),
                                        withJsonPath("$.fileId", is(fileId.toString()))
                                ))
                        ))));
    }

    @Test
    public void shouldHandleMarkAsSent() throws EventStreamException {

        final UUID notificationId = randomUUID();
        final Optional<String> clientContext = Optional.of(CLIENT_CONTEXT);
        final ZonedDateTime sentTime = new UtcClock().now();
        final ZonedDateTime completedAt = new UtcClock().now();

        final MarkAsSent markAsSent = markAsSent().withNotificationId(notificationId).withSentTime(sentTime).build();

        final Envelope<MarkAsSent> markAsSentEnvelope = envelopeFrom(metadataBuilder().withId(randomUUID())
                .withName("notificationnotify.command.mark-as-sent"), markAsSent);

        final NotificationSent notificationSent = new NotificationSent(clientContext, ofNullable(completedAt),
                Optional.of(""), ofNullable(""), notificationId, ofNullable(""), ofNullable(""), sentTime);

        when(eventSource.getStreamById(notificationId)).thenReturn(eventStream);
        when(aggregateService.get(eventStream, Notification.class)).thenReturn(aggregate);
        when(aggregate.markAsSent(sentTime, empty(), empty(),
                empty(), empty(), empty())).thenReturn(of(notificationSent));

        notifyCommandHandler.markAsSent(markAsSentEnvelope);

        verify(aggregateService).get(eventStream, Notification.class);

        assertThat(eventStream, eventStreamAppendedWith(streamContaining(
                jsonEnvelope()
                        .withMetadataOf(withMetadataEnvelopedFrom(envelopeFrom(markAsSentEnvelope.metadata(), NULL)))
                        .withPayloadOf(
                                payloadIsJson(allOf(
                                        withJsonPath("$.notificationId", is(notificationId.toString())),
                                        withJsonPath("$.sentTime", is(ZonedDateTimes.toString(sentTime)))
                                ))
                        ))));

    }

    @Test
    public void shouldHandleMarkAsSentWithEmail() throws EventStreamException {

        final UUID notificationId = randomUUID();
        final ZonedDateTime sentTime = new UtcClock().now();
        final ZonedDateTime completedAt = new UtcClock().now();
        final UUID caseId = UUID.randomUUID();
        final MarkAsSent markAsSent = markAsSent().withNotificationId(notificationId).withSentTime(sentTime).build();

        final Envelope<MarkAsSent> markAsSentEnvelope = envelopeFrom(metadataBuilder().withId(randomUUID())
                .withName("notificationnotify.command.mark-as-sent"), markAsSent);

        final NotificationSent notificationSent = NotificationSent
                .notificationSent()
                .withNotificationId(notificationId)
                .withSentTime(sentTime)
                .withSendToAddress("test21@hmcts.net")
                .withReplyToAddress("test01@hmcts.net")
                .withEmailBody("email body")
                .withEmailSubject("email subject")
                .withCompletedAt(completedAt)
                .build();

        when(eventSource.getStreamById(notificationId)).thenReturn(eventStream);
        when(aggregateService.get(eventStream, Notification.class)).thenReturn(aggregate);
        when(aggregate.markAsSent(sentTime, empty(), empty(),
                empty(), empty(), empty())).thenReturn(of(notificationSent));

        notifyCommandHandler.markAsSent(markAsSentEnvelope);

        verify(aggregateService).get(eventStream, Notification.class);

        assertThat(eventStream, eventStreamAppendedWith(streamContaining(
                jsonEnvelope()
                        .withMetadataOf(withMetadataEnvelopedFrom(envelopeFrom(markAsSentEnvelope.metadata(), NULL)))
                        .withPayloadOf(
                                payloadIsJson(allOf(
                                        withJsonPath("$.notificationId", is(notificationId.toString())),
                                        withJsonPath("$.sentTime", is(ZonedDateTimes.toString(sentTime))),
                                        withJsonPath("$.completedAt", is(ZonedDateTimes.toString(sentTime))),
                                        withJsonPath("$.sendToAddress", is("test21@hmcts.net")),
                                        withJsonPath("$.replyToAddress", is("test01@hmcts.net")),
                                        withJsonPath("$.emailSubject", is("email subject")),
                                        withJsonPath("$.emailBody", is("email body"))
                                ))
                        ))));

    }

    @Test
    public void shouldHandleMarkAsFailed() throws Exception {

        final UUID notificationId = randomUUID();
        final ZonedDateTime failedTime = new UtcClock().now();
        final int statusCode = 400;

        final MarkAsFailed markAsFailed = MarkAsFailed.markAsFailed()
                .withNotificationId(notificationId)
                .withFailedTime(failedTime)
                .withErrorMessage(ERROR_MESSAGE)
                .withStatusCode(statusCode)
                .build();

        final Envelope<MarkAsFailed> markAsFailedEnvelope = envelopeFrom(metadataBuilder().withId(randomUUID())
                .withName("notificationnotify.command.mark-as-failed"), markAsFailed);

        final NotificationFailed notificationFailed = NotificationFailed.notificationFailed()
                .withNotificationId(notificationId)
                .withErrorMessage(ERROR_MESSAGE)
                .withFailedTime(failedTime)
                .withStatusCode(statusCode)
                .build();

        when(eventSource.getStreamById(notificationId)).thenReturn(eventStream);
        when(aggregateService.get(eventStream, Notification.class)).thenReturn(aggregate);
        when(aggregate
                .markAsFailed(failedTime, ERROR_MESSAGE, Optional.of(statusCode)))
                .thenReturn(of(notificationFailed));

        notifyCommandHandler.markAsFailed(markAsFailedEnvelope);

        verify(aggregateService).get(eventStream, Notification.class);
        assertThat(eventStream, eventStreamAppendedWith(streamContaining(
                jsonEnvelope()
                        .withMetadataOf(withMetadataEnvelopedFrom(envelopeFrom(markAsFailedEnvelope.metadata(), NULL))
                                .withName("notificationnotify.events.notification-failed"))
                        .withPayloadOf(
                                payloadIsJson(allOf(
                                        withJsonPath("$.notificationId", is(notificationId.toString())),
                                        withJsonPath("$.failedTime", is(ZonedDateTimes.toString(failedTime)))
                                ))
                        ))));
    }

    @Test
    public void shouldHandleMarkAsAttempted() throws EventStreamException {

        final UUID notificationId = randomUUID();

        final ZonedDateTime attemptedTime = new UtcClock().now();

        final Integer statusCode = 400;

        final MarkAsAttempted markAsAttempted = markAsAttempted()
                .withAttemptedTime(attemptedTime)
                .withErrorMessage(ERROR_MESSAGE)
                .withNotificationId(notificationId)
                .withStatusCode(statusCode)
                .build();

        final NotificationAttempted notificationAttempted = NotificationAttempted.notificationAttempted()
                .withAttemptedTime(attemptedTime)
                .withNotificationId(notificationId)
                .withErrorMessage(ERROR_MESSAGE)
                .withStatusCode(statusCode)
                .build();

        final Envelope<MarkAsAttempted> markAsAttemptedEnvelope = envelopeFrom(metadataBuilder().withId(randomUUID())
                .withName("notificationnotify.command.mark-as-attempted"), markAsAttempted);

        when(eventSource.getStreamById(notificationId)).thenReturn(eventStream);
        when(aggregateService.get(eventStream, Notification.class)).thenReturn(aggregate);
        when(aggregate
                .markAsAttempted(attemptedTime, ERROR_MESSAGE, statusCode))
                .thenReturn(of(notificationAttempted));

        notifyCommandHandler.markAsAttempted(markAsAttemptedEnvelope);

        verify(aggregateService).get(eventStream, Notification.class);
        assertThat(eventStream, eventStreamAppendedWith(streamContaining(
                jsonEnvelope()
                        .withMetadataOf(withMetadataEnvelopedFrom(envelopeFrom(markAsAttemptedEnvelope.metadata(), NULL))
                                .withName("notificationnotify.events.notification-attempted"))
                        .withPayloadOf(
                                payloadIsJson(allOf(
                                        withJsonPath("$.notificationId", is(notificationId.toString())),
                                        withJsonPath("$.attemptedTime", is(ZonedDateTimes.toString(attemptedTime)))
                                ))
                        ))));

    }

    @Test
    public void shouldHandleSendLetterCommand() throws Exception {

        final UUID notificationId = randomUUID();
        final UUID caseId = randomUUID();
        final String letterUrl = "http://letterUrl";
        final LetterQueued letterQueued = letterQueued()
                .withLetterUrl(letterUrl)
                .withNotificationId(notificationId)
                .build();

        final SendLetterNotification sendLetterNotification = sendLetterNotification()
                .withNotificationId(notificationId)
                .withLetterUrl(letterUrl)
                .withPostage("first")
                .withClientContext(CLIENT_CONTEXT)
                .build();

        final Envelope<SendLetterNotification> sendLetterNotificationEnvelope = envelopeFrom(metadataBuilder().withId(randomUUID())
                .withName("notificationnotify.command.send-letter-notification"), sendLetterNotification);


        final EventStream eventStream = mock(EventStream.class);
        final Notification aggregate = mock(Notification.class);
        final Stream<Object> streamOfEvents = of(letterQueued);

        when(eventSource.getStreamById(notificationId)).thenReturn(eventStream);
        when(aggregateService.get(eventStream, Notification.class)).thenReturn(aggregate);
        when(aggregate.send(notificationId, letterUrl, Optional.of("first"), Optional.of(CLIENT_CONTEXT))).thenReturn(streamOfEvents);

        notifyCommandHandler.sendLetter(sendLetterNotificationEnvelope);

        assertThat(eventStream, eventStreamAppendedWith(streamContaining(
                jsonEnvelope()
                        .withMetadataOf(withMetadataEnvelopedFrom(envelopeFrom(sendLetterNotificationEnvelope.metadata(), NULL))
                                .withName("notificationnotify.events.letter-queued"))
                        .withPayloadOf(
                                payloadIsJson(allOf(
                                        withJsonPath("$.notificationId", is(notificationId.toString())),
                                        withJsonPath("$.letterUrl", is(letterUrl)))
                                ))
                        )));
    }

    @Test
    public void shouldHandleSendLetterCommandWhenRecipientTypeIsNull() throws Exception {

        final UUID notificationId = randomUUID();
        final UUID caseId = randomUUID();
        final String letterUrl = "http://letterUrl";
        final LetterQueued letterQueued = letterQueued()
                .withLetterUrl(letterUrl)
                .withNotificationId(notificationId)
                .build();

        final SendLetterNotification sendLetterNotification = sendLetterNotification()
                .withNotificationId(notificationId)
                .withLetterUrl(letterUrl)
                .withPostage("first")
                .withClientContext(CLIENT_CONTEXT)
                .build();

        final Envelope<SendLetterNotification> sendLetterNotificationEnvelope = envelopeFrom(metadataBuilder().withId(randomUUID())
                .withName("notificationnotify.command.send-letter-notification"), sendLetterNotification);


        final EventStream eventStream = mock(EventStream.class);
        final Notification aggregate = mock(Notification.class);
        final Stream<Object> streamOfEvents = of(letterQueued);

        when(eventSource.getStreamById(notificationId)).thenReturn(eventStream);
        when(aggregateService.get(eventStream, Notification.class)).thenReturn(aggregate);
        when(aggregate.send(notificationId, letterUrl, Optional.of("first"), Optional.of(CLIENT_CONTEXT))).thenReturn(streamOfEvents);

        notifyCommandHandler.sendLetter(sendLetterNotificationEnvelope);

        assertThat(eventStream, eventStreamAppendedWith(streamContaining(
                jsonEnvelope()
                        .withMetadataOf(withMetadataEnvelopedFrom(envelopeFrom(sendLetterNotificationEnvelope.metadata(), NULL))
                                .withName("notificationnotify.events.letter-queued"))
                        .withPayloadOf(
                                payloadIsJson(allOf(
                                        withJsonPath("$.notificationId", is(notificationId.toString())),
                                        withJsonPath("$.letterUrl", is(letterUrl))
                                ))
                        ))));
    }
    @Test
    public void shouldHandleResendLetterCommand() throws Exception {

        final UUID notificationId = randomUUID();
        final String letterUrl = "http://letterUrl";
        final ZonedDateTime now = now();
        final String failed = "failed";
        final String reason = "Validation failed automatic resend attempt";

        final LetterQueuedForResend letterQueuedForResend = letterQueuedForResend()
                .withNotificationId(notificationId)
                .withLetterUrl(letterUrl)
                .withReason(reason)
                .build();

        final MarkAsInvalid markAsInvalid = MarkAsInvalid.markAsInvalid()
                .withNotificationId(notificationId)
                .withErrorMessage(failed)
                .withFailedTime(now)
                .build();


        final Envelope<MarkAsInvalid> resendLetterNotificationEnvelope = envelopeFrom(metadataBuilder().withId(randomUUID())
                .withName("notificationnotify.command.mark-as-invalid"), markAsInvalid);


        final EventStream eventStream = mock(EventStream.class);
        final Notification aggregate = mock(Notification.class);
        final Stream<Object> streamOfEvents = of(letterQueuedForResend);

        when(eventSource.getStreamById(notificationId)).thenReturn(eventStream);
        when(aggregateService.get(eventStream, Notification.class)).thenReturn(aggregate);
        when(aggregate.markAsInvalid(notificationId, failed, now)).thenReturn(streamOfEvents);

        notifyCommandHandler.markAsInvalid(resendLetterNotificationEnvelope);

        assertThat(eventStream, eventStreamAppendedWith(streamContaining(
                jsonEnvelope()
                        .withMetadataOf(withMetadataEnvelopedFrom(envelopeFrom(resendLetterNotificationEnvelope.metadata(), NULL))
                                .withName("notificationnotify.events.letter-queued-for-resend"))
                        .withPayloadOf(
                                payloadIsJson(allOf(
                                        withJsonPath("$.notificationId", is(notificationId.toString())),
                                        withJsonPath("$.letterUrl", is(letterUrl)),
                                        withJsonPath("$.reason", is(reason))
                                ))
                        ))));
    }


    @Test
    public void shouldHandleProcessBouncedEmail() throws EventStreamException {

        final UUID notificationId = randomUUID();
        final ProcessBouncedEmail processBouncedEmail = processBouncedEmail().withNotificationId(notificationId).build();
        final Envelope<ProcessBouncedEmail> processBouncedEmailEnvelope = envelopeFrom(metadataBuilder().withId(randomUUID())
                .withName("notificationnotify.command.process-bounced-email"), processBouncedEmail);

        final EmailNotificationBounced notificationBounced = new EmailNotificationBounced(empty(), notificationId);
        when(eventSource.getStreamById(notificationId)).thenReturn(eventStream);
        when(aggregateService.get(eventStream, Notification.class)).thenReturn(aggregate);
        when(aggregate.processBouncedEmail()).thenReturn(of(notificationBounced));

        notifyCommandHandler.processBouncedEmail(processBouncedEmailEnvelope);

        verify(aggregateService).get(eventStream, Notification.class);

        assertThat(eventStream, eventStreamAppendedWith(streamContaining(
                jsonEnvelope()
                        .withMetadataOf(withMetadataEnvelopedFrom(envelopeFrom(processBouncedEmailEnvelope.metadata(), NULL)))
                        .withPayloadOf(
                                payloadIsJson(allOf(
                                        withJsonPath("$.notificationId", is(notificationId.toString()))
                                ))
                        ))));

    }

    @Test
    public void shouldRecordCheckBouncedEmailRequestFailed() throws EventStreamException {

        final RecordCheckBouncedEmailRequestFailed recordCheckBouncedEmailRequestFailed = RecordCheckBouncedEmailRequestFailed.recordCheckBouncedEmailRequestFailed().withServer("server").withReason("server down").build();
        final Envelope<RecordCheckBouncedEmailRequestFailed> recordCheckBouncedEmailRequestFailedEnvelope = envelopeFrom(metadataBuilder().withId(randomUUID())
                .withName("notificationnotify.command.check-bounced-emails"), recordCheckBouncedEmailRequestFailed);

        final CheckBouncedEmailRequestFailed checkBouncedEmailRequested = new CheckBouncedEmailRequestFailed("server down", "server");
        when(eventSource.getStreamById(any())).thenReturn(eventStream);
        when(aggregateService.get(eventStream, NotificationMonitor.class)).thenReturn(notificationMonitor);
        when(notificationMonitor.recordCheckBouncedEmailRequestFailed("server", "server down")).thenReturn(of(checkBouncedEmailRequested));

        notifyCommandHandler.recordBounceBackEmailRequestFailed(recordCheckBouncedEmailRequestFailedEnvelope);

        verify(aggregateService).get(eventStream, NotificationMonitor.class);
        assertThat(eventStream, eventStreamAppendedWith(streamContaining(
                jsonEnvelope()
                        .withMetadataOf(withMetadataEnvelopedFrom(envelopeFrom(recordCheckBouncedEmailRequestFailedEnvelope.metadata(), NULL)))
                        .withPayloadOf(
                                payloadIsJson(allOf(
                                        withJsonPath("$.server", is("server")),
                                        withJsonPath("$.reason", is("server down"))
                                ))
                        ))));

    }

    @Test
    public void shouldRecordCheckPocaEmailRequestFailed() throws EventStreamException {

        final RecordCheckPocaEmailRequestFailed recordCheckPocaEmailRequestFailed = RecordCheckPocaEmailRequestFailed.recordCheckPocaEmailRequestFailed().withServer("server").withReason("server down").build();
        final Envelope<RecordCheckPocaEmailRequestFailed> recordCheckPocaEmailRequestFailedEnvelope = envelopeFrom(metadataBuilder().withId(randomUUID())
                .withName("notificationnotify.command.process-poca-email"), recordCheckPocaEmailRequestFailed);

        final CheckPocaEmailRequestFailed checkPocaEmailRequestFailed = new CheckPocaEmailRequestFailed("server down", "server");
        when(eventSource.getStreamById(any())).thenReturn(eventStream);
        when(aggregateService.get(eventStream, NotificationMonitor.class)).thenReturn(notificationMonitor);
        when(notificationMonitor.recordCheckPocaEmailRequestFailed("server", "server down")).thenReturn(of(checkPocaEmailRequestFailed));

        notifyCommandHandler.recordPocaEmailRequestFailed(recordCheckPocaEmailRequestFailedEnvelope);

        verify(aggregateService).get(eventStream, NotificationMonitor.class);
        assertThat(eventStream, eventStreamAppendedWith(streamContaining(
                jsonEnvelope()
                        .withMetadataOf(withMetadataEnvelopedFrom(envelopeFrom(recordCheckPocaEmailRequestFailedEnvelope.metadata(), NULL)))
                        .withPayloadOf(
                                payloadIsJson(allOf(
                                        withJsonPath("$.server", is("server")),
                                        withJsonPath("$.reason", is("server down"))
                                ))
                        ))));

    }
}
