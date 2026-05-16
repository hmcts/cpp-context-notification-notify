package uk.gov.moj.cpp.notification.notify.filestore.azure;

import static com.azure.core.util.Context.NONE;
import static java.util.UUID.fromString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.UUID;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.options.BlobCopyFromUrlOptions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class FileIngestorTest {

    private static final UUID FILE_ID = fromString("184416a9-ef20-4500-a9c1-f64b87b424a9");
    private static final UUID CORRELATION_ID = fromString("384416a9-ef20-4500-a9c1-f64b87b424a0");
    private static final String FILENAME = "live_report_2026-05-15.csv";
    private static final URI SOURCE_URI = URI.create(
            "https://storage.blob.core.windows.net/mi-reportdata/published/reports/" + FILE_ID + "?sp=r&sig=test");

    @Mock
    private BlobContainerClient blobContainerClient;

    @Mock
    private BlobClient blobClient;

    @Mock
    private Logger logger;

    @InjectMocks
    private FileIngestor fileIngestor;

    @Captor
    private ArgumentCaptor<BlobCopyFromUrlOptions> copyOptionsCaptor;

    @Test
    public void shouldCopyBlobToInternalPathWithMetadata() {
        when(blobContainerClient.getBlobClient("internal/" + FILE_ID)).thenReturn(blobClient);

        fileIngestor.ingest(StoragePath.internal(), FILE_ID, CORRELATION_ID, FILENAME, SOURCE_URI);

        verify(blobClient).copyFromUrlWithResponse(copyOptionsCaptor.capture(), isNull(), eq(NONE));
        assertThat(copyOptionsCaptor.getValue().getCopySource(), is(SOURCE_URI.toString()));
        assertThat(copyOptionsCaptor.getValue().getMetadata().get("correlation_id"), is(CORRELATION_ID.toString()));
        assertThat(copyOptionsCaptor.getValue().getMetadata().get("filename"), is(FILENAME));
    }

    @Test
    public void shouldLogAfterSuccessfulIngest() {
        when(blobContainerClient.getBlobClient("internal/" + FILE_ID)).thenReturn(blobClient);

        fileIngestor.ingest(StoragePath.internal(), FILE_ID, CORRELATION_ID, FILENAME, SOURCE_URI);

        verify(logger).info("Ingested blob '{}' sourceUri='{}' correlationId='{}' filename='{}'",
                "internal/" + FILE_ID, SOURCE_URI, CORRELATION_ID, FILENAME);
    }

    @Test
    public void shouldPropagateBlobStorageExceptionOnCopyFailure() {
        final BlobStorageException blobStorageException = org.mockito.Mockito.mock(BlobStorageException.class);
        when(blobContainerClient.getBlobClient("internal/" + FILE_ID)).thenReturn(blobClient);
        doThrow(blobStorageException).when(blobClient).copyFromUrlWithResponse(
                isA(BlobCopyFromUrlOptions.class), isNull(), eq(NONE));

        assertThrows(BlobStorageException.class, () ->
                fileIngestor.ingest(StoragePath.internal(), FILE_ID, CORRELATION_ID, FILENAME, SOURCE_URI));
    }
}
