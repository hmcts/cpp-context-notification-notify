package uk.gov.moj.cpp.notification.notify.event.processor.task.handlers;

import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.TaskNames.CHECK_ACCEPTED_LETTER_STATUS_TASK;

import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExternalIdentifierJobState;

import javax.inject.Inject;

public class LetterAcceptedNextTaskExecutionHandler implements LetterAcceptedHandler {

    @Inject
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;

    @Inject
    private UtcClock utcClock;

    @Override
    public ExecutionInfo handle(final ExternalIdentifierJobState externalIdentifierJobState) {

        return ExecutionInfo.executionInfo()
                .withNextTask(CHECK_ACCEPTED_LETTER_STATUS_TASK)
                .withNextTaskStartTime(utcClock.now())
                .withJobData(objectToJsonObjectConverter.convert(externalIdentifierJobState))
                .withExecutionStatus(ExecutionStatus.INPROGRESS)
                .build();
    }
}
