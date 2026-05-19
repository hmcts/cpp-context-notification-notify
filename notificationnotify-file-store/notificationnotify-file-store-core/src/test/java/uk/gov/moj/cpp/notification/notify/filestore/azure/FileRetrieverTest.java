package uk.gov.moj.cpp.notification.notify.filestore.azure;

import static java.util.UUID.fromString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.specialized.BlobInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
public class FileRetrieverTest {

    private static final UUID FILE_ID = fromString("a1b2c3d4-0000-0000-0000-000000000001");

    @Mock
    private BlobContainerClient blobContainerClient;

    @Mock
    private BlobClient blobClient;

    @Mock
    private Logger logger;

    @InjectMocks
    private FileRetriever fileRetriever;

    @Test
    public void shouldReturnEmptyWhenBlobReturns404() {
        when(blobContainerClient.getBlobClient("internal/" + FILE_ID)).thenReturn(blobClient);
        final BlobStorageException blobStorageException = mock(BlobStorageException.class);
        when(blobStorageException.getStatusCode()).thenReturn(404);
        doThrow(blobStorageException).when(blobClient).openInputStream();

        final Optional<InputStream> result = fileRetriever.retrieve(StoragePath.internal(), FILE_ID);

        assertThat(result.isPresent(), is(false));
    }

    @Test
    public void shouldReturnStreamWhenBlobExists() {
        final BlobInputStream blobInputStream = mock(BlobInputStream.class);
        when(blobContainerClient.getBlobClient("internal/" + FILE_ID)).thenReturn(blobClient);
        doReturn(blobInputStream).when(blobClient).openInputStream();

        final Optional<InputStream> result = fileRetriever.retrieve(StoragePath.internal(), FILE_ID);

        assertThat(result.isPresent(), is(true));
        assertThat(result.get(), is((InputStream) blobInputStream));
    }

    @Test
    public void shouldRethrowWhenBlobStorageExceptionIsNotNotFound() {
        when(blobContainerClient.getBlobClient("internal/" + FILE_ID)).thenReturn(blobClient);
        final BlobStorageException blobStorageException = mock(BlobStorageException.class);
        when(blobStorageException.getStatusCode()).thenReturn(500);
        doThrow(blobStorageException).when(blobClient).openInputStream();

        assertThrows(BlobStorageException.class, () -> fileRetriever.retrieve(StoragePath.internal(), FILE_ID));
    }

    @Test
    public void shouldUseCorrectBlobPathForPublishedStoragePath() {
        final BlobInputStream blobInputStream = mock(BlobInputStream.class);
        when(blobContainerClient.getBlobClient("published/reports/" + FILE_ID)).thenReturn(blobClient);
        doReturn(blobInputStream).when(blobClient).openInputStream();

        fileRetriever.retrieve(StoragePath.published("reports"), FILE_ID);

        verify(blobContainerClient).getBlobClient("published/reports/" + FILE_ID);
    }
}
