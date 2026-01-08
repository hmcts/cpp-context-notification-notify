package uk.gov.moj.cpp.notification.notify.event.processor.task.letter;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.retry.RetryService;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.LetterAcceptedNextTaskExecutionHandler;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_LETTER_STATUS;

@ExtendWith(MockitoExtension.class)
public class CheckLetterStatusTaskTest {

    @Mock
    private CheckLetterStatusHandler checkLetterStatusHandler;

    @Mock
    private LetterAcceptedNextTaskExecutionHandler letterAcceptedNextTaskExecutionHandler;

    @Mock
    private RetryService retryService;

    @InjectMocks
    private CheckLetterStatusTask checkLetterStatusTask;

    @Test
    public void shouldDeleteToHandler() {
        final ExecutionInfo executionInfo = ExecutionInfo.executionInfo().build();

        checkLetterStatusTask.execute(executionInfo);

        verify(checkLetterStatusHandler).handle(executionInfo, letterAcceptedNextTaskExecutionHandler, CHECK_LETTER_STATUS);
    }

    @Test
    public void shouldReturnRetryDurations() {
        final Optional<List<Long>> durations = Optional.of(List.of(1L, 2L));

        when(retryService.getRetryDurationsInSecs(CHECK_LETTER_STATUS)).thenReturn(durations);

        final Optional<List<Long>> result = checkLetterStatusTask.getRetryDurationsInSecs();

        assertThat(result, is(durations));
    }
}
