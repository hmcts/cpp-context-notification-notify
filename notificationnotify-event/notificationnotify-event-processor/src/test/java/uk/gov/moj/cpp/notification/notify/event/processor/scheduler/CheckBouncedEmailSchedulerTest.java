package uk.gov.moj.cpp.notification.notify.event.processor.scheduler;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.STARTED;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_BOUNCED_EMAILS;

import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.moj.cpp.jobstore.api.ExecutionService;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;

import java.time.ZonedDateTime;

import javax.ejb.TimerService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CheckBouncedEmailSchedulerTest {

    @InjectMocks
    CheckBouncedEmailsScheduler checkBouncedEmailsScheduler;

    @Spy
    StringToJsonObjectConverter stringToJsonObjectConverter;

    @Mock
    private UtcClock utcClock;

    @Mock
    private TimerService timerService;

    @Mock
    private ExecutionService executionService;

    @Captor
    private ArgumentCaptor<ExecutionInfo> executionInfoCaptor;

    @BeforeEach
    public void setup() {
        setField(checkBouncedEmailsScheduler, "mailServerSettingsJson", "{\"mailServerCredentials\":[{\"server\":\"outlook.office365.com\",\"port\":\"-1\",\"username\":\"crime.sit.notifications3@HMCTS.NET\",\"password\":\"Hux39936\"}]}");
        setField(checkBouncedEmailsScheduler, "schedulerInterval", "121");
        checkBouncedEmailsScheduler.init();
    }

    @Test
    public void shouldInvokeExecutorServiceWhenTheTimerIsTriggered() {

        final ZonedDateTime nextStartTime = new UtcClock().now();
        when(utcClock.now()).thenReturn(nextStartTime);
        checkBouncedEmailsScheduler.startTimer();

        verify(executionService).executeWith(executionInfoCaptor.capture());
        final ExecutionInfo executionInfo = executionInfoCaptor.getValue();
        assertThat(executionInfo.getNextTask(), is(CHECK_BOUNCED_EMAILS.getTaskName()));
        assertThat(executionInfo.getExecutionStatus(), is(STARTED));
        assertThat(executionInfo.getNextTaskStartTime(), is(nextStartTime));
    }
}
