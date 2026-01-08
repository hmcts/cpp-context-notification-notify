package uk.gov.moj.cpp.notification.notify.event.processor.task.processors;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.NotificationJobState;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FailureSelectorTest {

    @InjectMocks
    private FailureSelector failureSelector;

    @Test
    public void shouldBeTemporaryFailureIfStatusCodeAnythingButBadRequest() throws Exception {
        final ErrorResponse errorResponse = mock(ErrorResponse.class);

        when(errorResponse.getStatusCode()).thenReturn(SC_NOT_FOUND, SC_INTERNAL_SERVER_ERROR, SC_SERVICE_UNAVAILABLE);

        assertThat(failureSelector.isTemporaryFailure(errorResponse), is(true));
    }

    @Test
    public void shouldBePermanentFailureIfStatusCodeIsBadRequest() throws Exception {

        final ErrorResponse errorResponse = new ErrorResponse("", SC_BAD_REQUEST);

        assertThat(failureSelector.isTemporaryFailure(errorResponse), is(false));
    }

    @Test
    public void shouldBePermanentFailureIfStatusCodeIsEmailFileAttachmentSizeTooBig() throws Exception {

        final ErrorResponse errorResponse = new ErrorResponse("", SC_REQUEST_ENTITY_TOO_LARGE);

        assertThat(failureSelector.isTemporaryFailure(errorResponse), is(false));
    }

    @Test
    public void shouldBePermanentFailureIfStatusCodeIsZero() throws Exception {

        final ErrorResponse errorResponse = mock(ErrorResponse.class);

        when(errorResponse.getStatusCode()).thenReturn(0);

        assertThat(failureSelector.isTemporaryFailure(errorResponse), is(false));
    }

    @Test
    public void shouldBeTemporaryFailureIfTheHttpStatusCodeIsZeroAndErrorMessageIsInputStreamException() throws Exception {

        final ErrorResponse errorResponse = mock(ErrorResponse.class);

        when(errorResponse.getStatusCode()).thenReturn(0);
        when(errorResponse.getErrorMessage()).thenReturn("Error when turning Base64InputStream into a string");

        assertThat(failureSelector.isTemporaryFailure(errorResponse), is(true));
    }

    @Test
    public void shouldBeAPermanentFailureIfTheHttpStatusCodeIsZeroAndErrorMessageIsNull() throws Exception {

        final ErrorResponse errorResponse = mock(ErrorResponse.class);

        when(errorResponse.getStatusCode()).thenReturn(0);
        when(errorResponse.getErrorMessage()).thenReturn(null);

        assertThat(failureSelector.isTemporaryFailure(errorResponse), is(false));
    }

    @Test
    public void shouldBeTemporaryFailureIfTheHttpStatusCodeIsZeroAndErrorMessageIsDownloadFailedException() throws Exception {

        final ErrorResponse errorResponse = mock(ErrorResponse.class);

        when(errorResponse.getStatusCode()).thenReturn(0);
        when(errorResponse.getErrorMessage()).thenReturn("Document download failed for Notification");

        assertThat(failureSelector.isTemporaryFailure(errorResponse), is(true));
    }
}
