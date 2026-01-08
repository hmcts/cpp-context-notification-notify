package uk.gov.moj.cpp.notification.notify.event.processor.task.letter;

import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_LETTER_STATUS;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.SEND_LETTER;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.TaskNames.SEND_LETTER_TASK;

import java.util.List;
import java.util.Optional;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.moj.cpp.jobstore.api.task.ExecutableTask;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.NotificationResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.SenderResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.retry.RetryService;
import uk.gov.moj.cpp.notification.notify.event.processor.sender.NotificationSender;
import uk.gov.moj.cpp.notification.notify.event.processor.sender.SenderFactory;
import uk.gov.moj.cpp.notification.notify.event.processor.task.Task;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.SendLetterDetailsJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.task.processors.SenderErrorResponseProcessor;
import uk.gov.moj.cpp.notification.notify.event.processor.task.processors.SuccessfulResponseProcessor;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.JsonObject;

import org.slf4j.Logger;

@uk.gov.moj.cpp.jobstore.api.annotation.Task(SEND_LETTER_TASK)
@ApplicationScoped
public class SendLetterTask implements ExecutableTask {

    @Inject
    private SenderFactory senderFactory;

    @Inject
    private JsonObjectToObjectConverter jsonObjectConverter;

    @Inject
    private SuccessfulResponseProcessor successfulResponseProcessor;

    @Inject
    private SenderErrorResponseProcessor senderErrorResponseProcessor;

    @SuppressWarnings({"squid:S1312"})//suppressing Sonar warning of logger not being static final
    @Inject
    private Logger logger;

    @Inject
    private RetryService retryService;

    @Override
    public Optional<List<Long>> getRetryDurationsInSecs() {
        return retryService.getRetryDurationsInSecs(SEND_LETTER);
    }

    @Override
    public ExecutionInfo execute(final ExecutionInfo executionInfo) {

        final Task task = SEND_LETTER;

        final JsonObject jobData = executionInfo.getJobData();
        final SendLetterDetailsJobState sendLetterDetailsJobState = jsonObjectConverter.convert(jobData, SendLetterDetailsJobState.class);

        logger.debug("Executing SEND LETTER task {}", sendLetterDetailsJobState.getNotificationId());

        logger.debug("Postage  value {}", sendLetterDetailsJobState.getTaskPayload().getPostage());

        final NotificationSender notificationSender = senderFactory.senderFor(task);
        final NotificationResponse notificationResponse = notificationSender.send(sendLetterDetailsJobState);

        if (notificationResponse.isSuccessful()) {
            return successfulResponseProcessor.handleSuccessfulResponse(
                    (SenderResponse) notificationResponse,
                    sendLetterDetailsJobState,
                    CHECK_LETTER_STATUS);
        }

        return senderErrorResponseProcessor.process(
                sendLetterDetailsJobState,
                (ErrorResponse) notificationResponse,
                task);
    }
}
