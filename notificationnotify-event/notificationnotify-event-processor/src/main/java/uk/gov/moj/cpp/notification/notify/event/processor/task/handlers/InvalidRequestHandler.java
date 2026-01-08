package uk.gov.moj.cpp.notification.notify.event.processor.task.handlers;

import static uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo.executionInfo;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.COMPLETED;

import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.NotificationNotifyCommandSender;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExternalIdentifierJobState;

import java.time.ZonedDateTime;
import java.util.UUID;

import javax.inject.Inject;

public class InvalidRequestHandler {

    @Inject
    private NotificationNotifyCommandSender notificationNotifyCommandSender;

    public ExecutionInfo handle(final ExternalIdentifierJobState externalIdentifierJobState,
                                final String errorMessage,
                                final ZonedDateTime failedTime) {
        final UUID notificationId = externalIdentifierJobState.getNotificationId();

        notificationNotifyCommandSender.markAsInvalid(notificationId, errorMessage, failedTime);
        return executionInfo()
                .withExecutionStatus(COMPLETED)
                .build();
    }
}
