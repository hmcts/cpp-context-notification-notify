package uk.gov.moj.cpp.notification.notify.event.processor.metrics;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.notification.notify.event.processor.metrics.MetricsCounter.EMAIL_PERMANENT_FAILURE_COUNTER;
import static uk.gov.moj.cpp.notification.notify.event.processor.metrics.MetricsCounter.EMAIL_SENT_COUNTER;
import static uk.gov.moj.cpp.notification.notify.event.processor.metrics.MetricsCounter.EMAIL_SUCCESS_COUNTER;
import static uk.gov.moj.cpp.notification.notify.event.processor.metrics.MetricsCounter.EMAIL_TEMPORARY_FAILURE_COUNTER;
import static uk.gov.moj.cpp.notification.notify.event.processor.metrics.MetricsCounter.LETTER_FAILED_TO_DOWNLOAD_FILE_COUNTER;
import static uk.gov.moj.cpp.notification.notify.event.processor.metrics.MetricsCounter.LETTER_PERMANENT_FAILURE_COUNTER;
import static uk.gov.moj.cpp.notification.notify.event.processor.metrics.MetricsCounter.LETTER_SENT_COUNTER;
import static uk.gov.moj.cpp.notification.notify.event.processor.metrics.MetricsCounter.LETTER_SUCCESS_COUNTER;
import static uk.gov.moj.cpp.notification.notify.event.processor.metrics.MetricsCounter.LETTER_TEMPORARY_FAILURE_COUNTER;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.SEND_EMAIL;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.SEND_LETTER;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MetricsTest {

    @Mock
    private MetricRegistry metricRegistry;

    @InjectMocks
    private Metrics metrics;

    @Test
    public void shouldIncrementTheCorrectSentCounterForSendEmailTask() throws Exception {

        final Counter counter = mock(Counter.class);

        when(metricRegistry.counter(EMAIL_SENT_COUNTER.getName())).thenReturn(counter);

        metrics.incrementSentCounter(SEND_EMAIL);

        verify(counter).inc();
    }

    @Test
    public void shouldIncrementTheCorrectSentCounterForSendLetterTask() throws Exception {

        final Counter counter = mock(Counter.class);

        when(metricRegistry.counter(LETTER_SENT_COUNTER.getName())).thenReturn(counter);

        metrics.incrementSentCounter(SEND_LETTER);

        verify(counter).inc();
    }

    @Test
    public void shouldIncrementTheCorrectSuccessCounterForSendEmailTask() throws Exception {

        final Counter counter = mock(Counter.class);

        when(metricRegistry.counter(EMAIL_SUCCESS_COUNTER.getName())).thenReturn(counter);

        metrics.incrementSuccessCounter(SEND_EMAIL);

        verify(counter).inc();
    }

    @Test
    public void shouldIncrementTheCorrectSuccessCounterForSendLetterTask() throws Exception {

        final Counter counter = mock(Counter.class);

        when(metricRegistry.counter(LETTER_SUCCESS_COUNTER.getName())).thenReturn(counter);

        metrics.incrementSuccessCounter(SEND_LETTER);

        verify(counter).inc();
    }

    @Test
    public void shouldIncrementTheCorrectTemporaryFailureCounterForSendEmailTask() throws Exception {

        final Counter counter = mock(Counter.class);

        when(metricRegistry.counter(EMAIL_TEMPORARY_FAILURE_COUNTER.getName())).thenReturn(counter);

        metrics.incrementTemporaryFailureCounter(SEND_EMAIL);

        verify(counter).inc();
    }

    @Test
    public void shouldIncrementTheCorrectTemporaryFailureCounterForSendLetterTask() throws Exception {

        final Counter counter = mock(Counter.class);

        when(metricRegistry.counter(LETTER_TEMPORARY_FAILURE_COUNTER.getName())).thenReturn(counter);

        metrics.incrementTemporaryFailureCounter(SEND_LETTER);

        verify(counter).inc();
    }

    @Test
    public void shouldIncrementTheCorrectPermanentFailureCounterForSendEmailTask() throws Exception {

        final Counter counter = mock(Counter.class);

        when(metricRegistry.counter(EMAIL_PERMANENT_FAILURE_COUNTER.getName())).thenReturn(counter);

        metrics.incrementPermanentFailureCounter(SEND_EMAIL);

        verify(counter).inc();
    }

    @Test
    public void shouldIncrementTheCorrectPermanentFailureCounterForSendLetterTask() throws Exception {

        final Counter counter = mock(Counter.class);

        when(metricRegistry.counter(LETTER_PERMANENT_FAILURE_COUNTER.getName())).thenReturn(counter);

        metrics.incrementPermanentFailureCounter(SEND_LETTER);

        verify(counter).inc();
    }

    @Test
    public void shouldIncrementLetterFailedToDownload() throws Exception {

        final Counter counter = mock(Counter.class);

        when(metricRegistry.counter(LETTER_FAILED_TO_DOWNLOAD_FILE_COUNTER.getName())).thenReturn(counter);

        metrics.incrementLetterFailedToDownload();

        verify(counter).inc();
    }
}
