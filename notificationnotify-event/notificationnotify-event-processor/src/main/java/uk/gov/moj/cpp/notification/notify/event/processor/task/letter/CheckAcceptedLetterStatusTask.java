package uk.gov.moj.cpp.notification.notify.event.processor.task.letter;

import java.util.List;
import java.util.Optional;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import uk.gov.moj.cpp.jobstore.api.task.ExecutableTask;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.retry.RetryService;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.LetterAcceptedRetryHandler;

import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_ACCEPTED_LETTER_STATUS;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.TaskNames.CHECK_ACCEPTED_LETTER_STATUS_TASK;

@uk.gov.moj.cpp.jobstore.api.annotation.Task(CHECK_ACCEPTED_LETTER_STATUS_TASK)
@ApplicationScoped
public class CheckAcceptedLetterStatusTask implements ExecutableTask {

    @Inject
    private CheckLetterStatusHandler checkLetterStatusHandler;

    @Inject
    private LetterAcceptedRetryHandler letterAcceptedRetryHandler;

    @Inject
    private RetryService retryService;

    public Optional<List<Long>> getRetryDurationsInSecs() {
        return retryService.getRetryDurationsInSecs(CHECK_ACCEPTED_LETTER_STATUS);
    }

    @Override
    public ExecutionInfo execute(final ExecutionInfo checkStatusExecutionInfo) {
        return checkLetterStatusHandler.handle(checkStatusExecutionInfo, letterAcceptedRetryHandler, CHECK_ACCEPTED_LETTER_STATUS);
    }
}

