package uk.gov.moj.cpp.notification.notify.event.processor.task.processors;

import static uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo.executionInfo;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.INPROGRESS;

import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.metrics.Metrics;
import uk.gov.moj.cpp.notification.notify.event.processor.response.SenderResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.task.Task;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExternalIdentifier;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExternalIdentifierJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.NotificationJobState;

import javax.inject.Inject;

public class SuccessfulResponseProcessor {

    @Inject
    private Metrics metrics;

    @Inject
    private ObjectToJsonObjectConverter objectConverter;

    @Inject
    private UtcClock clock;

    public ExecutionInfo handleSuccessfulResponse(
            final SenderResponse senderResponse,
            final NotificationJobState notificationJobState,
            final Task nextTask) {

        metrics.incrementSentCounter(nextTask);

        final ExternalIdentifierJobState externalIdentifierJobState = new ExternalIdentifierJobState(
                notificationJobState.getNotificationId(),
                new ExternalIdentifier(senderResponse.getExternalNotificationId(), senderResponse.getExtractedSendEmailResponse()));

        return executionInfo()
                .withJobData(objectConverter.convert(externalIdentifierJobState))
                .withNextTask(nextTask.getTaskName())
                .withNextTaskStartTime(clock.now())
                .withExecutionStatus(INPROGRESS)
                .build();
    }
}
