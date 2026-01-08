package uk.gov.moj.cpp.notification.notify.event.processor.task.email;

import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_EMAIL_STATUS;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.SEND_EMAIL;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.TaskNames.SEND_EMAIL_TASK;

import java.util.List;
import java.util.Optional;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.moj.cpp.jobstore.api.task.ExecutableTask;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.NotificationResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.Office365SenderResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.SenderResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.StatusResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.retry.RetryService;
import uk.gov.moj.cpp.notification.notify.event.processor.sender.NotificationSender;
import uk.gov.moj.cpp.notification.notify.event.processor.sender.SenderFactory;
import uk.gov.moj.cpp.notification.notify.event.processor.task.Task;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExternalIdentifier;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExtractedSendEmailResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.NotificationJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.SendEmailDetailsJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.CompleteHandler;
import uk.gov.moj.cpp.notification.notify.event.processor.task.processors.EmailStatusResponseProcessor;
import uk.gov.moj.cpp.notification.notify.event.processor.task.processors.SenderErrorResponseProcessor;
import uk.gov.moj.cpp.notification.notify.event.processor.task.processors.SuccessfulResponseProcessor;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.JsonObject;

import org.slf4j.Logger;

@uk.gov.moj.cpp.jobstore.api.annotation.Task(SEND_EMAIL_TASK)
@ApplicationScoped
public class SendEmailTask implements ExecutableTask {

    @Inject
    private SenderFactory senderFactory;

    @Inject
    private JsonObjectToObjectConverter jsonObjectConverter;

    @Inject
    private SuccessfulResponseProcessor successfulResponseProcessor;

    @Inject
    private SenderErrorResponseProcessor senderErrorResponseProcessor;

    @Inject
    private EmailStatusResponseProcessor emailStatusResponseProcessor;

    @SuppressWarnings({"squid:S1312"})//suppressing Sonar warning of logger not being static final
    @Inject
    private Logger logger;

    @Inject
    private RetryService retryService;

    @Inject
    private CompleteHandler completeHandler;

    @Override
    public Optional<List<Long>> getRetryDurationsInSecs() {
        return retryService.getRetryDurationsInSecs(SEND_EMAIL);
    }

    @Override
    public ExecutionInfo execute(final ExecutionInfo executionInfo) {

        final Task task = SEND_EMAIL;

        final JsonObject jobData = executionInfo.getJobData();
        final SendEmailDetailsJobState sendEmailDetailsJobState = jsonObjectConverter.convert(jobData, SendEmailDetailsJobState.class);

        final NotificationSender notificationSender = senderFactory.senderFor(task);
        final NotificationResponse notificationResponse = notificationSender.send(sendEmailDetailsJobState);

        if (notificationResponse.isSuccessful()) {
            if (notificationResponse instanceof Office365SenderResponse office365SenderResponse) {
                logger.debug("Processing Check Mail Status from Office 365 with notification response {}", notificationResponse);
                ExtractedSendEmailResponse extractedSendEmailResponse = office365SenderResponse.getExtractedSendEmailResponse();
                ExternalIdentifier externalIdentifier = new ExternalIdentifier(sendEmailDetailsJobState.getNotificationId(),
                                                                                extractedSendEmailResponse);
                NotificationJobState<ExternalIdentifier> notificationJobState = new NotificationJobState(sendEmailDetailsJobState.getNotificationId(), externalIdentifier);

          
                return emailStatusResponseProcessor.process(
                        notificationJobState,
                        new StatusResponse(NotificationStatus.DELIVERED),
                        CHECK_EMAIL_STATUS);
            }
            return successfulResponseProcessor.handleSuccessfulResponse(
                    (SenderResponse) notificationResponse,
                    sendEmailDetailsJobState,
                    CHECK_EMAIL_STATUS);
        }

        return senderErrorResponseProcessor.process(
                sendEmailDetailsJobState,
                (ErrorResponse) notificationResponse,
                task);
    }
}
