package uk.gov.moj.cpp.notification.notify.event.processor.task.handlers;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import javax.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus;
import uk.gov.moj.cpp.notification.notify.event.processor.task.Task;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.NotificationFailedJobState;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.TaskNames.NOTIFICATION_FAILED_TASK;

@ExtendWith(MockitoExtension.class)
class NotificationFailedTaskFactoryTest {

    @Mock
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;

    @Mock
    private UtcClock utcClock;

    @Captor
    private ArgumentCaptor<NotificationFailedJobState> notificationFailedJobStateArgumentCaptor;

    @InjectMocks
    private NotificationFailedTaskFactory notificationFailedTaskFactory;

    @Test
    void execute_shouldBuildExecutionInfoWithExhaustTaskDetails() {
        final UUID notificationId = UUID.randomUUID();
        final Task failedTask = Task.SEND_EMAIL;
        final String errorMessage = "error_message";
        final Integer statusCode = 500;
        final ZonedDateTime now = ZonedDateTime.now();
        final JsonObject jsonJobData = mock(JsonObject.class);
        when(utcClock.now()).thenReturn(now);
        when(objectToJsonObjectConverter.convert(any())).thenReturn(jsonJobData);

        final ExecutionInfo result = notificationFailedTaskFactory.create(notificationId, failedTask, errorMessage, Optional.of(statusCode));

        assertThat(result.getNextTask(), is(NOTIFICATION_FAILED_TASK));
        assertThat(result.getNextTaskStartTime(), is(now));
        assertThat(result.getExecutionStatus(), is(ExecutionStatus.INPROGRESS));
        assertThat(result.getJobData(), is(jsonJobData));

        verify(objectToJsonObjectConverter).convert(notificationFailedJobStateArgumentCaptor.capture());
        final NotificationFailedJobState notificationFailedJobState = notificationFailedJobStateArgumentCaptor.getValue();
        assertThat(notificationFailedJobState.getNotificationId(), is(notificationId));
        assertThat(notificationFailedJobState.getTaskPayload().errorMessage(), is(errorMessage));
        assertThat(notificationFailedJobState.getTaskPayload().statusCode(), is(statusCode));
        assertThat(notificationFailedJobState.getTaskPayload().failedTask(), is(failedTask));
    }
}