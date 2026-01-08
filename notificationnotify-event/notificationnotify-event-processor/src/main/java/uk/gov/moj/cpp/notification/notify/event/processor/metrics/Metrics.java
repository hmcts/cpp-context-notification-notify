package uk.gov.moj.cpp.notification.notify.event.processor.metrics;

import static uk.gov.moj.cpp.notification.notify.event.processor.metrics.MetricsCounter.EMAIL_PERMANENT_FAILURE_COUNTER;
import static uk.gov.moj.cpp.notification.notify.event.processor.metrics.MetricsCounter.EMAIL_SENT_COUNTER;
import static uk.gov.moj.cpp.notification.notify.event.processor.metrics.MetricsCounter.EMAIL_SUCCESS_COUNTER;
import static uk.gov.moj.cpp.notification.notify.event.processor.metrics.MetricsCounter.EMAIL_TEMPORARY_FAILURE_COUNTER;
import static uk.gov.moj.cpp.notification.notify.event.processor.metrics.MetricsCounter.LETTER_FAILED_TO_DOWNLOAD_FILE_COUNTER;
import static uk.gov.moj.cpp.notification.notify.event.processor.metrics.MetricsCounter.LETTER_PERMANENT_FAILURE_COUNTER;
import static uk.gov.moj.cpp.notification.notify.event.processor.metrics.MetricsCounter.LETTER_SENT_COUNTER;
import static uk.gov.moj.cpp.notification.notify.event.processor.metrics.MetricsCounter.LETTER_SUCCESS_COUNTER;
import static uk.gov.moj.cpp.notification.notify.event.processor.metrics.MetricsCounter.LETTER_TEMPORARY_FAILURE_COUNTER;

import uk.gov.moj.cpp.notification.notify.event.processor.task.Task;

import javax.inject.Inject;

import com.codahale.metrics.MetricRegistry;

public class Metrics {

    @Inject
    private MetricRegistry metricRegistry;

    public void incrementSentCounter(final Task task) {

        metricRegistry.counter(sentCounterFor(task)).inc();
    }

    public void incrementSuccessCounter(final Task task) {

        metricRegistry.counter(successCounterFor(task)).inc();
    }

    public void incrementTemporaryFailureCounter(final Task task) {

        metricRegistry.counter(temporaryFailureCounterFor(task)).inc();
    }

    public void incrementPermanentFailureCounter(final Task task) {

        metricRegistry.counter(permanentFailureCounterFor(task)).inc();
    }

    public void incrementLetterFailedToDownload() {

        metricRegistry.counter(LETTER_FAILED_TO_DOWNLOAD_FILE_COUNTER.getName()).inc();
    }

    private String sentCounterFor(final Task task) {

        if (task.isEmailTask()) {
            return EMAIL_SENT_COUNTER.getName();
        }

        return LETTER_SENT_COUNTER.getName();
    }

    private String successCounterFor(final Task task) {

        if (task.isEmailTask()) {
            return EMAIL_SUCCESS_COUNTER.getName();
        }

        return LETTER_SUCCESS_COUNTER.getName();
    }

    private String temporaryFailureCounterFor(final Task task) {

        if (task.isEmailTask()) {
            return EMAIL_TEMPORARY_FAILURE_COUNTER.getName();
        }

        return LETTER_TEMPORARY_FAILURE_COUNTER.getName();
    }

    private String permanentFailureCounterFor(final Task task) {

        if (task.isEmailTask()) {
            return EMAIL_PERMANENT_FAILURE_COUNTER.getName();
        }

        return LETTER_PERMANENT_FAILURE_COUNTER.getName();
    }
}
