package uk.gov.moj.cpp.notification.notify.filestore.azure;

import static java.util.UUID.fromString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobRange;

import java.io.OutputStream;
import java.util.Optional;
import java.util.UUID;

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
    public void shouldReturnEmptyOptionalWhenBlobDoesNotExist() {
        when(blobContainerClient.getBlobClient("internal/" + FILE_ID)).thenReturn(blobClient);
        when(blobClient.exists()).thenReturn(false);

        final Optional<byte[]> result = fileRetriever.retrieve(StoragePath.internal(), FILE_ID);

        assertThat(result.isPresent(), is(false));
    }

    @Test
    public void shouldReturnBytesWhenBlobExists() {
        final byte[] expectedBytes = "blob content".getBytes();
        when(blobContainerClient.getBlobClient("internal/" + FILE_ID)).thenReturn(blobClient);
        when(blobClient.exists()).thenReturn(true);
        doAnswer(invocation -> {
            final OutputStream outputStream = invocation.getArgument(0);
            outputStream.write(expectedBytes);
            return null;
        }).when(blobClient).downloadStreamWithResponse(
                isA(OutputStream.class), isA(BlobRange.class),
                isNull(), isNull(), eq(false), isNull(), isNull());

        final Optional<byte[]> result = fileRetriever.retrieve(StoragePath.internal(), FILE_ID);

        assertThat(result.isPresent(), is(true));
        assertThat(result.get(), is(expectedBytes));
    }

    @Test
    public void shouldUseCorrectBlobPathForPublishedStoragePath() {
        when(blobContainerClient.getBlobClient("published/reports/" + FILE_ID)).thenReturn(blobClient);
        when(blobClient.exists()).thenReturn(false);

        fileRetriever.retrieve(StoragePath.published("reports"), FILE_ID);

        verify(blobContainerClient).getBlobClient("published/reports/" + FILE_ID);
    }

    @Test
    public void shouldReturnEmptyOptionalWhenBlobDoesNotExistForInboxPath() {
        when(blobContainerClient.getBlobClient("inbox/sdg-output/" + FILE_ID)).thenReturn(blobClient);
        when(blobClient.exists()).thenReturn(false);

        final Optional<byte[]> result = fileRetriever.retrieve(StoragePath.inbox("sdg-output"), FILE_ID);

        assertThat(result.isPresent(), is(false));
    }
}
