package uk.gov.moj.cpp.notification.notify.event.processor;

import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.STARTED;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.SEND_EMAIL;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.SEND_LETTER;

import uk.gov.justice.json.schemas.domains.notificationnotify.FirstClassLetterQueued;
import uk.gov.justice.json.schemas.domains.notificationnotify.LetterQueued;
import uk.gov.justice.json.schemas.domains.notificationnotify.LetterQueuedForResend;
import uk.gov.justice.json.schemas.domains.notificationnotify.NotificationQueued;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.jobstore.api.ExecutionService;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.jobstore.persistence.Priority;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.SendEmailDetails;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.SendEmailDetailsJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.SendLetterDetails;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.SendLetterDetailsJobState;

import java.util.UUID;

import javax.inject.Inject;
import javax.interceptor.Interceptor;
import javax.json.JsonObject;

import org.slf4j.Logger;

@ServiceComponent(EVENT_PROCESSOR)
public class NotificationNotifyEventProcessor {

    private static final String FIRST_CLASS_POSTAGE = "first";

    @Inject
    private UtcClock utcClock;
    @Inject
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;
    @Inject
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;
    @Inject
    private ExecutionService executionService;
    @SuppressWarnings({"squid:S1312"})//suppressing Sonar warning of logger not being static final
    @Inject
    private Logger logger;

    @Handles("notificationnotify.events.notification-queued")
    public void createNotificationJob(final JsonEnvelope event) {

        final JsonObject notificationPayload = event.payloadAsJsonObject();

        final NotificationQueued notificationQueued = jsonObjectToObjectConverter.convert(notificationPayload, NotificationQueued.class);
        final UUID notificationId = notificationQueued.getNotificationId();

        final SendEmailDetails sendEmailDetails = new SendEmailDetails(
                notificationQueued.getTemplateId(),
                notificationQueued.getSendToAddress(),
                notificationQueued.getReplyToAddress(),
                notificationQueued.getReplyToAddressId(),
                notificationQueued.getPersonalisation(),
                notificationQueued.getMaterialUrl(),
                notificationQueued.getFileId());

        final SendEmailDetailsJobState sendEmailDetailsJobState = new SendEmailDetailsJobState(
                notificationId,
                sendEmailDetails);

        final JsonObject emailJsonObject = objectToJsonObjectConverter.convert(sendEmailDetailsJobState);

        final ExecutionInfo executionInfo = new ExecutionInfo(emailJsonObject, SEND_EMAIL.getTaskName(), utcClock.now(), STARTED, Priority.MEDIUM);

        logger.info("Adding notification {} to the jobstore for later execution", notificationId);

        executionService.executeWith(executionInfo);
    }

    @Handles("notificationnotify.events.letter-queued")
    public void createLetterNotificationJob(final JsonEnvelope event) {
        final LetterQueued letterQueued = jsonObjectToObjectConverter.convert(event.payloadAsJsonObject(), LetterQueued.class);
        createLetterNotification(letterQueued.getNotificationId(), letterQueued.getLetterUrl(), "");
    }

    @Handles("notificationnotify.events.first-class-letter-queued")
    public void createLetterNotificationJobForFirstClass(final JsonEnvelope event) {
        final FirstClassLetterQueued firstClassLetterQueued = jsonObjectToObjectConverter.convert(event.payloadAsJsonObject(), FirstClassLetterQueued.class);
        createLetterNotification(firstClassLetterQueued.getNotificationId(), firstClassLetterQueued.getLetterUrl(), FIRST_CLASS_POSTAGE);
    }

    @Handles("notificationnotify.events.letter-queued-for-resend")
    public void createLetterNotificationJobForResend(final JsonEnvelope event) {
        final LetterQueuedForResend letterQueued = jsonObjectToObjectConverter.convert(event.payloadAsJsonObject(), LetterQueuedForResend.class);
        createLetterNotification(letterQueued.getNotificationId(), letterQueued.getLetterUrl(), letterQueued.getPostage().orElse(""));
    }


    private void createLetterNotification(final UUID notificationId, final String letterUrl, final String postage) {

        final SendLetterDetails sendLetterDetails = new SendLetterDetails(letterUrl, postage);

        final SendLetterDetailsJobState sendLetterDetailsJobState = new SendLetterDetailsJobState(
                notificationId,
                sendLetterDetails);

        final JsonObject letterJsonObject = objectToJsonObjectConverter.convert(sendLetterDetailsJobState);

        final ExecutionInfo executionInfo = new ExecutionInfo(letterJsonObject, SEND_LETTER.getTaskName(), utcClock.now(), STARTED, Priority.MEDIUM);

        logger.info("Adding notification {} to the jobstore for later execution", sendLetterDetailsJobState.getNotificationId());

        logger.info("Postage value :: {} from Send Letter Details", sendLetterDetailsJobState.getTaskPayload().getPostage());

        executionService.executeWith(executionInfo);
    }


}
