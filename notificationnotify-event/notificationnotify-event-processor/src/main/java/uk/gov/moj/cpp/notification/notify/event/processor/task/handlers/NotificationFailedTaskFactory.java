package uk.gov.moj.cpp.notification.notify.event.processor.task.handlers;

import java.util.Optional;
import java.util.UUID;
import javax.inject.Inject;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.task.Task;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.NotificationFailedDetails;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.NotificationFailedJobState;

import static uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo.executionInfo;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.INPROGRESS;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.TaskNames.NOTIFICATION_FAILED_TASK;

public class NotificationFailedTaskFactory {

    @Inject
    private UtcClock utcClock;

    @Inject
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;

    public ExecutionInfo create(final UUID notificationId,
                                final Task failedTask,
                                final String errorMessage,
                                final Optional<Integer> statusCode) {

        final NotificationFailedDetails notificationFailedDetails = new NotificationFailedDetails(errorMessage, statusCode.orElse(null), failedTask);
        final NotificationFailedJobState notificationFailedJobState = new NotificationFailedJobState(notificationId, notificationFailedDetails);

        return executionInfo()
                .withNextTask(NOTIFICATION_FAILED_TASK)
                .withNextTaskStartTime(utcClock.now())
                .withExecutionStatus(INPROGRESS)
                .withJobData(objectToJsonObjectConverter.convert(notificationFailedJobState))
                .build();
    }
}
