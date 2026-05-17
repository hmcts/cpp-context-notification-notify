package uk.gov.moj.cpp.notification.notify.filestore.azure;

import static java.util.UUID.fromString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.UUID;

import com.azure.core.util.polling.SyncPoller;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobCopyInfo;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.options.BlobBeginCopyOptions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
public class FileIngestorTest {

    private static final UUID FILE_ID = fromString("184416a9-ef20-4500-a9c1-f64b87b424a9");
    private static final UUID CORRELATION_ID = fromString("384416a9-ef20-4500-a9c1-f64b87b424a0");
    private static final String FILENAME = "live_report_2026-05-15.csv";
    private static final URI SOURCE_URI = URI.create(
            "https://storage.blob.core.windows.net/mi-reportdata/published/reports/" + FILE_ID);

    @Mock
    private BlobContainerClient blobContainerClient;

    @Mock
    private BlobClient blobClient;

    @Mock
    private SyncPoller<BlobCopyInfo, Void> syncPoller;

    @Mock
    private Logger logger;

    @InjectMocks
    private FileIngestor fileIngestor;

    @Captor
    private ArgumentCaptor<BlobBeginCopyOptions> copyOptionsCaptor;

    @Test
    public void shouldCopyBlobToInternalPathWithMetadata() {
        when(blobContainerClient.getBlobClient("internal/" + FILE_ID)).thenReturn(blobClient);
        when(blobClient.beginCopy(isA(BlobBeginCopyOptions.class))).thenReturn(syncPoller);

        fileIngestor.ingest(StoragePath.internal(), FILE_ID, CORRELATION_ID, FILENAME, SOURCE_URI);

        verify(blobClient).beginCopy(copyOptionsCaptor.capture());
        assertThat(copyOptionsCaptor.getValue().getSourceUrl(), is(SOURCE_URI.toString()));
        assertThat(copyOptionsCaptor.getValue().getMetadata().get("correlation_id"), is(CORRELATION_ID.toString()));
        assertThat(copyOptionsCaptor.getValue().getMetadata().get("filename"), is(FILENAME));
    }

    @Test
    public void shouldWaitForCopyCompletionBeforeLogging() {
        when(blobContainerClient.getBlobClient("internal/" + FILE_ID)).thenReturn(blobClient);
        when(blobClient.beginCopy(isA(BlobBeginCopyOptions.class))).thenReturn(syncPoller);

        fileIngestor.ingest(StoragePath.internal(), FILE_ID, CORRELATION_ID, FILENAME, SOURCE_URI);

        verify(syncPoller).waitForCompletion();
        verify(logger).info("Ingested blob '{}' sourceUri='{}' correlationId='{}' filename='{}'",
                "internal/" + FILE_ID, SOURCE_URI, CORRELATION_ID, FILENAME);
    }

    @Test
    public void shouldPropagateBlobStorageExceptionOnCopyFailure() {
        final BlobStorageException blobStorageException = org.mockito.Mockito.mock(BlobStorageException.class);
        when(blobContainerClient.getBlobClient("internal/" + FILE_ID)).thenReturn(blobClient);
        doThrow(blobStorageException).when(blobClient).beginCopy(isA(BlobBeginCopyOptions.class));

        assertThrows(BlobStorageException.class, () ->
                fileIngestor.ingest(StoragePath.internal(), FILE_ID, CORRELATION_ID, FILENAME, SOURCE_URI));
    }
}
