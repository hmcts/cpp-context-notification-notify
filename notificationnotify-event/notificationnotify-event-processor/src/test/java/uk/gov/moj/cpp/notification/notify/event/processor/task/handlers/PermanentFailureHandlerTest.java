package uk.gov.moj.cpp.notification.notify.event.processor.task.handlers;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_EMAIL_STATUS;

import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.task.processors.ErrorMessageFactory;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PermanentFailureHandlerTest {

    @Mock
    private ErrorMessageFactory errorMessageFactory;

    @Mock
    private NotificationFailedTaskFactory notificationFailedTaskFactory;

    @InjectMocks
    private PermanentFailureHandler permanentFailureHandler;

    @Test
    public void shouldHandlePermanentFailureWithErrorMessage() {

        final UUID notificationId = UUID.randomUUID();
        final ExecutionInfo executionInfo = ExecutionInfo.executionInfo().build();
        final String message = "message";

        when(notificationFailedTaskFactory.create(notificationId, CHECK_EMAIL_STATUS, message, empty())).thenReturn(executionInfo);

        final ExecutionInfo result = permanentFailureHandler.handle(notificationId, CHECK_EMAIL_STATUS, message);

        assertThat(result, is(executionInfo));
    }

    @Test
    public void shouldHandlePermanentFailureWithErrorResponse() {

        final UUID notificationId = UUID.randomUUID();
        final ErrorResponse errorResponse = mock(ErrorResponse.class);
        final ExecutionInfo executionInfo = ExecutionInfo.executionInfo().build();
        final String errorMessage = "error message";

        when(errorResponse.getStatusCode()).thenReturn(SC_BAD_REQUEST);
        when(errorMessageFactory.createErrorMessage(
                notificationId,
                errorResponse,
                CHECK_EMAIL_STATUS)).thenReturn(errorMessage);
        when(notificationFailedTaskFactory.create(notificationId, CHECK_EMAIL_STATUS, errorMessage, of(SC_BAD_REQUEST))).thenReturn(executionInfo);

        final ExecutionInfo result = permanentFailureHandler.handle(notificationId, errorResponse, CHECK_EMAIL_STATUS);

        assertThat(result, is(executionInfo));
    }
}
