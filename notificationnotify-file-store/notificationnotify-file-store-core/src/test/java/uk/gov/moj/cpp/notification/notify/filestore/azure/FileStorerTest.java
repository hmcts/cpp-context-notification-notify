package uk.gov.moj.cpp.notification.notify.filestore.azure;

import static com.azure.core.util.Context.NONE;
import static java.util.UUID.fromString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.options.BlobParallelUploadOptions;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
public class FileStorerTest {

    private static final UUID CORRELATION_ID = fromString("c0ff1234-0000-0000-0000-000000000001");

    @Mock
    private BlobContainerClient blobContainerClient;

    @Mock
    private BlobClient blobClient;

    @Mock
    private Logger logger;

    @InjectMocks
    private FileStorer fileStorer;

    @Captor
    private ArgumentCaptor<BlobParallelUploadOptions> uploadOptionsCaptor;

    @Test
    public void shouldUploadBlobWithCorrelationIdAndFilenameMetadata() {
        final byte[] content = "attachment content".getBytes();
        when(blobContainerClient.getBlobClient(startsWith("internal/"))).thenReturn(blobClient);

        final UUID fileId = fileStorer.store(StoragePath.internal(), CORRELATION_ID, "report.pdf", content);

        assertThat(fileId, notNullValue());
        verify(blobContainerClient).getBlobClient("internal/" + fileId);
        verify(blobClient).uploadWithResponse(uploadOptionsCaptor.capture(), isNull(), eq(NONE));
        assertThat(uploadOptionsCaptor.getValue().getMetadata().get("correlation_id"), is(CORRELATION_ID.toString()));
        assertThat(uploadOptionsCaptor.getValue().getMetadata().get("filename"), is("report.pdf"));
    }

    @Test
    public void shouldStoreBlobUnderPublishedPathPrefix() {
        final byte[] content = "report bytes".getBytes();
        when(blobContainerClient.getBlobClient(startsWith("published/reports/"))).thenReturn(blobClient);

        final UUID fileId = fileStorer.store(StoragePath.published("reports"), CORRELATION_ID, "monthly.csv", content);

        verify(blobContainerClient).getBlobClient("published/reports/" + fileId);
    }
}
