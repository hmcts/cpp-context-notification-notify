package uk.gov.moj.cpp.notification.notify.event.processor.retry;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import uk.gov.justice.services.common.configuration.GlobalValue;
import uk.gov.moj.cpp.notification.notify.event.processor.task.Task;

import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_ACCEPTED_LETTER_STATUS;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_EMAIL_STATUS;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_LETTER_STATUS;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.SEND_EMAIL;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.SEND_LETTER;

@ApplicationScoped
public class RetryService {

    //   All durations in seconds
    @Inject
    @GlobalValue(key = "notify.email.retry.threshold.durations", defaultValue = "60,300,1800,3600,7200,14400")
    private String emailRetryDurations;

    @Inject
    @GlobalValue(key = "notify.letter.retry.threshold.durations", defaultValue = "60,300,1800,3600,7200,14400,14400")
    private String letterRetryDurations;

    @Inject
    @GlobalValue(key = "notify.letter.accepted.retry.threshold.durations", defaultValue = "60,300,1800,3600,7200,14400")
    private String letterAcceptedRetryDurations;

    @Inject
    @GlobalValue(key = "notify.letter.received.retry.threshold.durations", defaultValue = "43200,43200,43200,43200,43200,43200,43200,43200,43200,43200,43200,43200,43200,43200,43200,43200")
    private String letterReceivedRetryDurations;

    private final Map<Task, List<Long>> durationsMap = new EnumMap<>(Task.class);

    @PostConstruct
    public void init() {
        try {
            splitRetryDurations(emailRetryDurations, SEND_EMAIL);
            splitRetryDurations(emailRetryDurations, CHECK_EMAIL_STATUS);
            splitRetryDurations(letterRetryDurations, SEND_LETTER);
            splitRetryDurations(letterAcceptedRetryDurations, CHECK_LETTER_STATUS);
            splitRetryDurations(letterReceivedRetryDurations, CHECK_ACCEPTED_LETTER_STATUS);
        } catch (NumberFormatException e) {
            throw new NotifyConfigurationException("Error parsing notify retry threshold duration value", e);
        }
    }

    private void splitRetryDurations(final String retryDurations, final Task task) {

        final List<Long> durations = Arrays.stream(retryDurations.split(","))
                .map(String::trim)
                .map(Long::parseLong)
                .toList();
        durationsMap.put(task, durations);
    }

    public Optional<List<Long>> getRetryDurationsInSecs(final Task task) {
        return Optional.ofNullable(durationsMap.get(task));
    }

    public int noOfOfConfiguredRetryAttempts(final Task task) {
        return Optional.ofNullable(durationsMap.get(task))
                .map(List::size)
                .orElse(0);
    }
}
