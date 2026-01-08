package uk.gov.moj.cpp.notification.notify.event.processor.task.letter;

import java.util.List;
import java.util.Optional;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import uk.gov.moj.cpp.jobstore.api.task.ExecutableTask;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.retry.RetryService;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.LetterAcceptedNextTaskExecutionHandler;

import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_LETTER_STATUS;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.TaskNames.CHECK_LETTER_STATUS_TASK;

@uk.gov.moj.cpp.jobstore.api.annotation.Task(CHECK_LETTER_STATUS_TASK)
@ApplicationScoped
public class CheckLetterStatusTask implements ExecutableTask {

    @Inject
    private CheckLetterStatusHandler checkLetterStatusHandler;

    @Inject
    private LetterAcceptedNextTaskExecutionHandler letterAcceptedNextTaskExecutionHandler;

    @Inject
    private RetryService retryService;

    @Override
    public Optional<List<Long>> getRetryDurationsInSecs() {
        return retryService.getRetryDurationsInSecs(CHECK_LETTER_STATUS);
    }

    @Override
    public ExecutionInfo execute(final ExecutionInfo executionInfo) {
        return checkLetterStatusHandler.handle(executionInfo, letterAcceptedNextTaskExecutionHandler, CHECK_LETTER_STATUS);
    }
}
