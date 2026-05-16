package uk.gov.moj.cpp.notification.notify.filestore.test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.options.BlobParallelUploadOptions;

import java.io.OutputStream;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class BlobStoreTestHelperTest {

    @Mock
    private BlobContainerClient blobContainerClient;

    @Mock
    private BlobClient blobClient;

    @Captor
    private ArgumentCaptor<BlobParallelUploadOptions> uploadOptionsCaptor;

    @Test
    public void shouldReturnContainerName() {
        final BlobStoreTestHelper helper = new BlobStoreTestHelper(blobContainerClient, "notificationnotify-files");

        assertThat(helper.containerName(), is("notificationnotify-files"));
    }

    @Test
    public void shouldUploadBlobToCorrectPath() {
        final BlobStoreTestHelper helper = new BlobStoreTestHelper(blobContainerClient, "notificationnotify-files");
        final byte[] content = "pdf content".getBytes();

        when(blobContainerClient.getBlobClient(org.mockito.ArgumentMatchers.startsWith("inbox/notification-templates/")))
                .thenReturn(blobClient);

        final UUID fileId = helper.upload("inbox/notification-templates", "template.pdf", content);

        assertThat(fileId, notNullValue());
        verify(blobContainerClient).getBlobClient("inbox/notification-templates/" + fileId);
        verify(blobClient).uploadWithResponse(uploadOptionsCaptor.capture(), isNull(), eq(com.azure.core.util.Context.NONE));

        final java.util.Map<String, String> metadata = uploadOptionsCaptor.getValue().getMetadata();
        assertThat(metadata.get("filename"), is("template.pdf"));
        assertThat(metadata.get("correlation_id"), is(fileId.toString()));
    }

    @Test
    public void shouldReturnEmptyWhenBlobDoesNotExistOnDownload() {
        final BlobStoreTestHelper helper = new BlobStoreTestHelper(blobContainerClient, "notificationnotify-files");
        final UUID fileId = UUID.randomUUID();

        when(blobContainerClient.getBlobClient("published/reports/" + fileId)).thenReturn(blobClient);
        when(blobClient.exists()).thenReturn(false);

        final Optional<byte[]> result = helper.download("published/reports", fileId);

        assertThat(result.isPresent(), is(false));
    }

    @Test
    public void shouldReturnTrueWhenBlobExists() {
        final BlobStoreTestHelper helper = new BlobStoreTestHelper(blobContainerClient, "notificationnotify-files");
        final UUID fileId = UUID.randomUUID();

        when(blobContainerClient.getBlobClient("inbox/docs/" + fileId)).thenReturn(blobClient);
        when(blobClient.exists()).thenReturn(true);

        assertThat(helper.exists("inbox/docs", fileId), is(true));
    }

    @Test
    public void shouldReturnFalseWhenBlobDoesNotExist() {
        final BlobStoreTestHelper helper = new BlobStoreTestHelper(blobContainerClient, "notificationnotify-files");
        final UUID fileId = UUID.randomUUID();

        when(blobContainerClient.getBlobClient("inbox/docs/" + fileId)).thenReturn(blobClient);
        when(blobClient.exists()).thenReturn(false);

        assertThat(helper.exists("inbox/docs", fileId), is(false));
    }

    @Test
    public void shouldReturnBytesWhenBlobExistsOnDownload() {
        final BlobStoreTestHelper helper = new BlobStoreTestHelper(blobContainerClient, "notificationnotify-files");
        final UUID fileId = UUID.randomUUID();
        final byte[] expectedBytes = "blob content".getBytes();

        when(blobContainerClient.getBlobClient("internal/" + fileId)).thenReturn(blobClient);
        when(blobClient.exists()).thenReturn(true);
        doAnswer(invocation -> {
            final OutputStream outputStream = invocation.getArgument(0);
            outputStream.write(expectedBytes);
            return null;
        }).when(blobClient).downloadStreamWithResponse(any(OutputStream.class), any(BlobRange.class),
                isNull(), isNull(), eq(false), isNull(), isNull());

        final Optional<byte[]> result = helper.download("internal", fileId);

        assertThat(result.isPresent(), is(true));
        assertThat(result.get(), is(expectedBytes));
    }

    @Test
    public void shouldCallDeleteIfExistsOnDelete() {
        final BlobStoreTestHelper helper = new BlobStoreTestHelper(blobContainerClient, "notificationnotify-files");
        final UUID fileId = UUID.randomUUID();

        when(blobContainerClient.getBlobClient("internal/" + fileId)).thenReturn(blobClient);

        helper.delete("internal", fileId);

        verify(blobClient).deleteIfExists();
    }
}
