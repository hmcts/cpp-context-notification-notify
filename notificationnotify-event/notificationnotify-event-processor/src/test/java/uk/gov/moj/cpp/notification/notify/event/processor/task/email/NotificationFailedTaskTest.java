package uk.gov.moj.cpp.notification.notify.event.processor.task.email;

import java.util.Optional;
import java.util.UUID;
import javax.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus;
import uk.gov.moj.cpp.notification.notify.event.processor.NotificationNotifyCommandSender;
import uk.gov.moj.cpp.notification.notify.event.processor.metrics.Metrics;
import uk.gov.moj.cpp.notification.notify.event.processor.task.Task;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.NotificationFailedDetails;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.NotificationFailedJobState;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationFailedTaskTest {

    private final ObjectToJsonObjectConverter objectToJsonObjectConverter = new ObjectToJsonObjectConverter(new ObjectMapperProducer().objectMapper());

    @Mock
    private NotificationNotifyCommandSender notificationNotifyCommandSender;

    @Mock
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;

    @Mock
    private Logger logger;

    @Mock
    private Metrics metrics;

    @InjectMocks
    private NotificationFailedTask task;

    @Test
    void execute_shouldSendNotificationFailedCommand() {
        final String errorMessage = "error_message";
        final UUID notificationId = UUID.randomUUID();
        final int statusCode = 500;
        final NotificationFailedJobState notificationFailedJobState = new NotificationFailedJobState(notificationId,
                new NotificationFailedDetails(errorMessage, statusCode, Task.SEND_EMAIL));
        final ExecutionInfo executionInfo = buildExecutionInfo(notificationFailedJobState);
        when(jsonObjectToObjectConverter.convert(executionInfo.getJobData(), NotificationFailedJobState.class)).thenReturn(notificationFailedJobState);

        final ExecutionInfo result = task.execute(executionInfo);

        assertThat(result.getExecutionStatus(), is(ExecutionStatus.COMPLETED));
        verify(metrics).incrementPermanentFailureCounter(Task.SEND_EMAIL);
        verify(notificationNotifyCommandSender).markNotificationFailed(notificationId, "Failed to send notification. error_message", Optional.of(statusCode));
    }

    private ExecutionInfo buildExecutionInfo(final NotificationFailedJobState notificationFailedJobState) {
        final JsonObject jobData = objectToJsonObjectConverter.convert(notificationFailedJobState);
        return ExecutionInfo.executionInfo()
                .withNextTask(Task.TaskNames.NOTIFICATION_FAILED_TASK)
                .withJobData(jobData)
                .build();
    }
}