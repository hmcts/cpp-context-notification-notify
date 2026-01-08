package uk.gov.moj.cpp.notification.notify.event.processor.task.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.NotificationNotifyCommandSender;
import uk.gov.moj.cpp.notification.notify.event.processor.metrics.Metrics;
import uk.gov.moj.cpp.notification.notify.event.processor.task.Task;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExternalIdentifier;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExtractedSendEmailResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.NotificationEmailDetails;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.NotificationJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.util.PersonalisationExtractor;

import javax.inject.Inject;
import java.time.ZonedDateTime;
import java.util.Optional;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo.executionInfo;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.COMPLETED;

public class CompleteHandler {

    @Inject
    private NotificationNotifyCommandSender notificationNotifyCommandSender;

    @Inject
    private PersonalisationExtractor personalisationExtractor;

    @Inject
    private Metrics metrics;

    private static final Logger LOGGER = LoggerFactory.getLogger(CompleteHandler.class.getName());

    public ExecutionInfo handle(final NotificationJobState<ExternalIdentifier> notificationJobState, final Task task) {
        LOGGER.info("Starting to handle notification job for task: {}", task.getTaskName());
        metrics.incrementSuccessCounter(task);

        if (task.isEmailTask()) {
            ExtractedSendEmailResponse extractedSendEmailResponse = (ExtractedSendEmailResponse) ofNullable(notificationJobState)
                    .map(n -> n.getTaskPayload())
                    .map(e -> (ExternalIdentifier) e)
                    .map(ExternalIdentifier::getExtractedSendEmailResponse)
                    .orElseThrow(() -> new IllegalStateException("ExtractedSendEmailResponse is missing"));

            NotificationEmailDetails notificationEmailDetails = buildNotificationEmailDetails(extractedSendEmailResponse);
            ofNullable(notificationJobState).ifPresent( n ->
                    notificationNotifyCommandSender.markAsSent(n.getNotificationId(), notificationEmailDetails));

        } else {
            notificationNotifyCommandSender.markAsSent(notificationJobState.getNotificationId(), NotificationEmailDetails.emailDetails().withCompletedAt(ZonedDateTime.now()).build());
        }

        return executionInfo()
                .withExecutionStatus(COMPLETED)
                .build();
    }

    private NotificationEmailDetails buildNotificationEmailDetails( ExtractedSendEmailResponse extractedSendEmailResponse) {
        try {
            final String emailSubject =  extractedSendEmailResponse.getEmailSubject();
            final String emailBody =  extractedSendEmailResponse.getEmailBody();
            final String replyToAddress = extractedSendEmailResponse.getEmailReplyToAddress();
            final String sendToAddress = extractedSendEmailResponse.getEmailSendToAddress();

            return NotificationEmailDetails.emailDetails()
                    .withReplyToAddress(replyToAddress)
                    .withSendToAddress(sendToAddress)
                    .withEmailBody(emailBody)
                    .withEmailSubject(emailSubject)
                    .withCompletedAt(ZonedDateTime.now())
                    .build();
        } catch (Exception e) {
            LOGGER.error("Error occurred while building NotificationEmailDetails", e);
            return NotificationEmailDetails.emailDetails().withCompletedAt(ZonedDateTime.now()).build();
        }
    }
}
