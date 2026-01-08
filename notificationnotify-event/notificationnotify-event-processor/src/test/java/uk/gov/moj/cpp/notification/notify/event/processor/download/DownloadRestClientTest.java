package uk.gov.moj.cpp.notification.notify.event.processor.download;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.common.http.HeaderConstants.USER_ID;

import uk.gov.moj.cpp.systemusers.ServiceContextSystemUserProvider;

import java.util.UUID;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DownloadRestClientTest {

    @Mock
    private ServiceContextSystemUserProvider serviceContextSystemUserProvider;

    @Mock
    private MediaTypeSelector mediaTypeSelector;

    @Mock
    private ClientCreator clientCreator;

    @InjectMocks
    private DownloadRestClient downloadRestClient;

    @Test
    public void shouldReturnResponseFromUrl() {

        final Client client = mock(Client.class);
        final WebTarget webTarget = mock(WebTarget.class);
        final Invocation.Builder builder = mock(Invocation.Builder.class);
        final Response response = mock(Response.class);
        final UUID systemUserId = UUID.randomUUID();

        final String documentUrl = "http://localhost:8080/document";

        when(clientCreator.createNewClient()).thenReturn(client);
        when(mediaTypeSelector.mediaTypeFor(documentUrl)).thenReturn(APPLICATION_OCTET_STREAM);
        when(serviceContextSystemUserProvider.getContextSystemUserId()).thenReturn(of(systemUserId));

        when(client.target(documentUrl)).thenReturn(webTarget);
        when(webTarget.request()).thenReturn(builder);
        when(builder.accept(APPLICATION_OCTET_STREAM)).thenReturn(builder);
        when(builder.header(USER_ID, systemUserId.toString())).thenReturn(builder);
        when(builder.get()).thenReturn(response);

        final Response actualResponse = downloadRestClient.download(documentUrl);

        assertThat(actualResponse, is(response));
    }

    @Test
    public void shouldThrowExceptionIfSystemUserIsNotPresent() {

        when(serviceContextSystemUserProvider.getContextSystemUserId()).thenReturn(empty());

        try {
            downloadRestClient.download("http://localhost:8080/document");
            fail();
        } catch (final DownloadClientException e) {
            assertThat(e.getMessage(), is("Failed to retrieve System User Id"));
        }
    }
}