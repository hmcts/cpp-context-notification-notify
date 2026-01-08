package uk.gov.moj.cpp.notification.notify.paas;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.logging.Logger;

import com.google.common.io.Resources;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Unit test for Function class.
 */
public class GenerateEmailNotificationTest {
    /**
     * Unit test for HttpTriggerJava method.
     */


    @Test
    @Timeout(value = 50, unit = SECONDS)
    public void testHttpTriggerJava() throws Exception {

        // Setup
        @SuppressWarnings("unchecked") final HttpRequestMessage<Optional<String>> req = mock(HttpRequestMessage.class);

        final URL url = Resources.getResource("json/request.json");
        final String payload = Resources.toString(url, StandardCharsets.UTF_8);


        doReturn(Optional.of(payload)).when(req).getBody();

        doAnswer(new Answer<HttpResponseMessage.Builder>() {
            @Override
            public HttpResponseMessage.Builder answer(InvocationOnMock invocation) {
                HttpStatus status = (HttpStatus) invocation.getArguments()[0];
                return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
            }
        }).when(req).createResponseBuilder(any(HttpStatus.class));

        final ExecutionContext context = mock(ExecutionContext.class);
        doReturn(Logger.getGlobal()).when(context).getLogger();

        final String template = "Template";
        final String html = "<html>HTML</html>";
        final String templateId = "c7482552-ba85-4feb-8e91-771ccf17764a";

        // Invoke
        final HttpResponseMessage ret = new GenerateEmailNotification().run(
                req,
                templateId,
                template.getBytes(),
                html.getBytes(),
                context
        );

        // Verify
        assertThat(ret.getStatus(), is(HttpStatus.INTERNAL_SERVER_ERROR));

    }
}
