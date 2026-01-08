package uk.gov.moj.cpp.notification.notify.event.processor.retry;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_ACCEPTED_LETTER_STATUS;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_BOUNCED_EMAILS;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_EMAIL_STATUS;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_LETTER_STATUS;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.SEND_EMAIL;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.SEND_LETTER;

public class RetryServiceTest {

    private RetryService retryService;

    @BeforeEach
    void setUp() {
        retryService = new RetryService();
        ReflectionUtil.setField(retryService, "emailRetryDurations", "1, 60,120 ");
        ReflectionUtil.setField(retryService, "letterRetryDurations", "1, 60,121 ");
        ReflectionUtil.setField(retryService, "letterAcceptedRetryDurations", "1, 60,122 ");
        ReflectionUtil.setField(retryService, "letterReceivedRetryDurations", "1, 60,123 ");
        retryService.init();
    }

    @Test
    public void shouldParseStringAndReturnRetryDurationsInSecs_givenTask() {
        final Optional<List<Long>> emailStatusRetries = retryService.getRetryDurationsInSecs(CHECK_EMAIL_STATUS);
        assertThat(emailStatusRetries.get(), is(List.of(1L, 60L, 120L)));

        final Optional<List<Long>> emailSendRetries = retryService.getRetryDurationsInSecs(SEND_EMAIL);
        assertThat(emailSendRetries.get(), is(List.of(1L, 60L, 120L)));

        final Optional<List<Long>> letterSendRetries = retryService.getRetryDurationsInSecs(SEND_LETTER);
        assertThat(letterSendRetries.get(), is(List.of(1L, 60L, 121L)));

        final Optional<List<Long>> letterAcceptedRetries = retryService.getRetryDurationsInSecs(CHECK_LETTER_STATUS);
        assertThat(letterAcceptedRetries.get(), is(List.of(1L, 60L, 122L)));

        final Optional<List<Long>> letterReceivedRetries = retryService.getRetryDurationsInSecs(CHECK_ACCEPTED_LETTER_STATUS);
        assertThat(letterReceivedRetries.get(), is(List.of(1L, 60L, 123L)));
    }

    @Test
    public void shouldReturnNoOfConfiguredRetryAttempts_givenTask() {
        final int noOfOfConfiguredRetryAttempts = retryService.noOfOfConfiguredRetryAttempts(CHECK_EMAIL_STATUS);
        assertThat(noOfOfConfiguredRetryAttempts, is(3));
    }

    @Test
    public void shouldReturnNoOfConfiguredRetryAttemptsAsZero_givenConfigNotFoundForTask() {
        final int noOfOfConfiguredRetryAttempts = retryService.noOfOfConfiguredRetryAttempts(CHECK_BOUNCED_EMAILS);
        assertThat(noOfOfConfiguredRetryAttempts, is(0));
    }
}