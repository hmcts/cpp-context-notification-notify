package uk.gov.moj.cpp.notification.notify.event.processor.task.processors;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.retry.RetryService;

import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.SEND_EMAIL;

@ExtendWith(MockitoExtension.class)
public class ErrorMessageFactoryTest {

    @Mock
    private RetryService retryService;

    @InjectMocks
    private ErrorMessageFactory errorMessageFactory;

    @Test
    public void shouldReturnBadRequestErrorMessage() {
        final UUID notificationId = randomUUID();
        final ErrorResponse errorResponse = mock(ErrorResponse.class);
        final String errorResponseMessage = "error response message";

        when(errorResponse.getStatusCode()).thenReturn(SC_BAD_REQUEST);
        when(errorResponse.getErrorMessage()).thenReturn(errorResponseMessage);

        final String errorMessage = errorMessageFactory.createErrorMessage(notificationId, errorResponse, SEND_EMAIL);

        assertThat(errorMessage, is(format("Permanent failure while trying to send-email for notification job %s. Bad Request (400). %s", notificationId, errorResponseMessage)));
    }

    @Test
    public void shouldReturnRetryFailureErrorMessage() {
        final UUID notificationId = randomUUID();
        final ErrorResponse errorResponse = mock(ErrorResponse.class);
        final String errorResponseMessage = "error response message";

        when(errorResponse.getStatusCode()).thenReturn(0);
        when(errorResponse.getErrorMessage()).thenReturn(errorResponseMessage);
        when(retryService.noOfOfConfiguredRetryAttempts(SEND_EMAIL)).thenReturn(2);

        final String errorMessage = errorMessageFactory.createErrorMessage(notificationId, errorResponse, SEND_EMAIL);

        assertThat(errorMessage, is(format("Failed to send-email after 2 attempts. %s", errorResponseMessage)));
    }
}