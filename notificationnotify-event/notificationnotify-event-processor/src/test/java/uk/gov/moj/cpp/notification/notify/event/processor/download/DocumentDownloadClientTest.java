package uk.gov.moj.cpp.notification.notify.event.processor.download;

import static javax.ws.rs.core.HttpHeaders.CONTENT_LOCATION;
import static org.apache.commons.io.IOUtils.toByteArray;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.notification.notify.event.processor.metrics.Metrics;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import javax.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
public class DocumentDownloadClientTest {

    @Mock
    private DownloadRestClient restClient;

    @Mock
    private Metrics metrics;

    @Mock
    private Logger logger;

    @Spy
    private MediaTypeSelector mediaTypeSelector;

    @InjectMocks
    private DocumentDownloadClient documentDownloadClient;

    @Test
    public void shouldSucceedReturningDocument() throws IOException {
        final String documentURL = "http://xyz.com/xyz";

        final Response response = mock(Response.class);
        final int contentLength = 1000;

        when(restClient.download(documentURL)).thenReturn(response);
        when(response.getStatus()).thenReturn(SC_OK);
        when(response.readEntity(InputStream.class)).thenReturn(new ByteArrayInputStream("example".getBytes()));
        when(response.getHeaderString(CONTENT_LOCATION)).thenReturn("filename=sampleFile.pdf");

        final DocumentDownloadResponse documentDownloadResponse = documentDownloadClient.getDocument(documentURL);

        assertThat(documentDownloadResponse.downloadSuccessful(), is(true));

        final SuccessfulDocumentDownload successfulDocumentDownload = (SuccessfulDocumentDownload) documentDownloadResponse;

        assertThat(successfulDocumentDownload.getHttpResult(), is(SC_OK));
        assertThat(successfulDocumentDownload.getFileName(), is("sampleFile.pdf"));
        assertThat(successfulDocumentDownload.getBytes(), is("example".getBytes()));
    }

    @Test
    public void shouldSucceedReturningDocumentFromMaterial() throws IOException, URISyntaxException {
        final String documentURL = "http://material-query-api/xyz";

        final Response response = mock(Response.class);
        final int contentLength = 1000;

        when(restClient.download(documentURL)).thenReturn(response);
        when(response.getStatus()).thenReturn(SC_OK);
        final ClassLoader classLoader = getClass().getClassLoader();
        final File file1 = new File(classLoader.getResource("pdf/JohnBloggs.pdf").getFile());
        //when(response.getLocation()).thenReturn(new URI("file:" + file1.getAbsolutePath()));
        when(response.getLocation()).thenReturn(file1.toURI());


        when(response.getHeaderString(CONTENT_LOCATION)).thenReturn("filename=sampleFile.pdf");

        final DocumentDownloadResponse documentDownloadResponse = documentDownloadClient.getDocument(documentURL);

        assertThat(documentDownloadResponse.downloadSuccessful(), is(true));

        final SuccessfulDocumentDownload successfulDocumentDownload = (SuccessfulDocumentDownload) documentDownloadResponse;

        assertThat(successfulDocumentDownload.getHttpResult(), is(SC_OK));
        assertThat(successfulDocumentDownload.getFileName(), is("sampleFile.pdf"));
        final InputStream stream = new URL("file:" + file1.getAbsolutePath()).openStream();

        assertThat(successfulDocumentDownload.getBytes(), is( toByteArray(stream)));
    }

    @Test
    public void shouldFailReturningDocument() throws IOException {
        final String documentURL = "http://xyz.com/xyz";
        final String responseBody = "Error message";
        final Response response = mock(Response.class);
        final int contentLength = 1000;


        when(restClient.download(documentURL)).thenReturn(response);
        when(response.getStatus()).thenReturn(SC_NOT_FOUND);

        final DocumentDownloadResponse documentDownloadResponse = documentDownloadClient.getDocument(documentURL);

        assertThat(documentDownloadResponse.downloadSuccessful(), is(false));

        final UnsuccessfulDocumentDownload unsuccessfulDocumentDownload = (UnsuccessfulDocumentDownload) documentDownloadResponse;

        assertThat(unsuccessfulDocumentDownload.getResponseBody(), is("Failed to download PDF document: Response code '404'"));
        assertThat(unsuccessfulDocumentDownload.getHttpResult(), is(SC_NOT_FOUND));
        verify(logger).error("Failed to download PDF document: Response code '404'");
        verify(metrics).incrementLetterFailedToDownload();
    }
}
