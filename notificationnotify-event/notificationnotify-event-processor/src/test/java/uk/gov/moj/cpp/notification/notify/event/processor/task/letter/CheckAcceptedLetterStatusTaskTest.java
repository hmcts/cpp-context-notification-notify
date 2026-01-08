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
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.LetterAcceptedRetryHandler;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_ACCEPTED_LETTER_STATUS;

@ExtendWith(MockitoExtension.class)
class CheckAcceptedLetterStatusTaskTest {

    @Mock
    private CheckLetterStatusHandler checkLetterStatusHandler;

    @Mock
    private LetterAcceptedRetryHandler letterAcceptedRetryHandler;

    @Mock
    private RetryService retryService;

    @InjectMocks
    private CheckAcceptedLetterStatusTask checkAcceptedLetterStatusTask;

    @Test
    public void shouldDeleteToHandler() {
        final ExecutionInfo executionInfo = ExecutionInfo.executionInfo().build();

        checkAcceptedLetterStatusTask.execute(executionInfo);

        verify(checkLetterStatusHandler).handle(executionInfo, letterAcceptedRetryHandler, CHECK_ACCEPTED_LETTER_STATUS);
    }

    @Test
    public void shouldReturnRetryDurations() {
        final Optional<List<Long>> durations = Optional.of(List.of(1L, 2L));

        when(retryService.getRetryDurationsInSecs(CHECK_ACCEPTED_LETTER_STATUS)).thenReturn(durations);

        final Optional<List<Long>> result = checkAcceptedLetterStatusTask.getRetryDurationsInSecs();

        assertThat(result, is(durations));
    }

}