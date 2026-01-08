package uk.gov.moj.cpp.notification.notify.event.processor.task.email;

import java.util.Optional;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.JsonObject;
import org.slf4j.Logger;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.moj.cpp.jobstore.api.task.ExecutableTask;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.NotificationNotifyCommandSender;
import uk.gov.moj.cpp.notification.notify.event.processor.metrics.Metrics;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.NotificationFailedDetails;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.NotificationFailedJobState;

import static java.lang.String.format;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo.executionInfo;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.COMPLETED;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.TaskNames.NOTIFICATION_FAILED_TASK;

@uk.gov.moj.cpp.jobstore.api.annotation.Task(NOTIFICATION_FAILED_TASK)
@ApplicationScoped
public class NotificationFailedTask implements ExecutableTask {

    @Inject
    private Metrics metrics;
    @Inject
    private NotificationNotifyCommandSender notificationNotifyCommandSender;

    @Inject
    private JsonObjectToObjectConverter jsonObjectConverter;

    @SuppressWarnings({"squid:S1312"})//suppressing Sonar warning of logger not being static final
    @Inject
    private Logger logger;

    @Override
    public ExecutionInfo execute(final ExecutionInfo executionInfo) {

        final JsonObject jobData = executionInfo.getJobData();
        final NotificationFailedJobState notificationFailedJobState = jsonObjectConverter.convert(jobData, NotificationFailedJobState.class);
        final NotificationFailedDetails notificationFailedDetails = notificationFailedJobState.getTaskPayload();

        logger.debug("Executing SEND NOTIFICATION_FAILED task {}", notificationFailedJobState.getNotificationId());

        metrics.incrementPermanentFailureCounter(notificationFailedDetails.failedTask());

        notificationNotifyCommandSender.markNotificationFailed(
                notificationFailedJobState.getNotificationId(),
                format("Failed to send notification. %s", notificationFailedDetails.errorMessage()),
                Optional.ofNullable(notificationFailedDetails.statusCode()));

        return executionInfo()
                .withExecutionStatus(COMPLETED)
                .build();
    }
}
