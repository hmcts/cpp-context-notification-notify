package uk.gov.moj.cpp.notification.notify.filestore.azure;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;

import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.options.BlobParallelUploadOptions;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

public class FileRetrieverIT {

    // Standard Azurite well-known test credential — not a real secret.
    // See: https://learn.microsoft.com/azure/storage/common/storage-use-azurite
    private static final String AZURITE_CONNECTION_STRING =
            "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;" +
            "AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/" +
            "K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://localhost:10000/devstoreaccount1;";

    private static final String CONTAINER_NAME = "fileretriever-it-" + UUID.randomUUID().toString().substring(0, 8);

    private BlobContainerClient blobContainerClient;
    private FileRetriever fileRetriever;

    @BeforeEach
    public void setUp() {
        final BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(AZURITE_CONNECTION_STRING)
                .buildClient();
        blobContainerClient = blobServiceClient.getBlobContainerClient(CONTAINER_NAME);
        blobContainerClient.createIfNotExists();

        fileRetriever = new FileRetriever();
        setField(fileRetriever, "blobContainerClient", blobContainerClient);
        setField(fileRetriever, "logger", LoggerFactory.getLogger(FileRetriever.class));
    }

    @AfterEach
    public void tearDown() {
        blobContainerClient.deleteIfExists();
    }

    @Test
    public void shouldRetrieveStoredBlobContent() {
        final byte[] content = "retrieved content".getBytes();
        final UUID fileId = UUID.randomUUID();
        uploadBlob("internal/" + fileId, content);

        final Optional<byte[]> result = fileRetriever.retrieve(StoragePath.internal(), fileId);

        assertThat(result.isPresent(), is(true));
        assertThat(result.get(), is(content));
    }

    @Test
    public void shouldReturnEmptyWhenBlobDoesNotExist() {
        final UUID fileId = UUID.randomUUID();

        final Optional<byte[]> result = fileRetriever.retrieve(StoragePath.internal(), fileId);

        assertThat(result.isPresent(), is(false));
    }

    @Test
    public void shouldRetrieveBlobFromPublishedPrefix() {
        final byte[] content = "report content".getBytes();
        final UUID fileId = UUID.randomUUID();
        uploadBlob("published/reports/" + fileId, content);

        final Optional<byte[]> result = fileRetriever.retrieve(StoragePath.published("reports"), fileId);

        assertThat(result.isPresent(), is(true));
        assertThat(result.get(), is(content));
    }

    @Test
    public void shouldRoundTripWithFileStorer() {
        final byte[] originalContent = "round-trip document".getBytes();
        final UUID correlationId = UUID.randomUUID();

        final FileStorer fileStorer = new FileStorer();
        setField(fileStorer, "blobContainerClient", blobContainerClient);
        setField(fileStorer, "logger", LoggerFactory.getLogger(FileStorer.class));

        final UUID fileId = fileStorer.store(StoragePath.internal(), correlationId, "doc.pdf", originalContent);
        final Optional<byte[]> retrieved = fileRetriever.retrieve(StoragePath.internal(), fileId);

        assertThat(retrieved.isPresent(), is(true));
        assertThat(retrieved.get(), is(originalContent));
    }

    private void uploadBlob(final String blobName, final byte[] content) {
        blobContainerClient.getBlobClient(blobName)
                .uploadWithResponse(
                        new BlobParallelUploadOptions(BinaryData.fromBytes(content))
                                .setMetadata(Map.of("correlation_id", UUID.randomUUID().toString(), "filename", blobName)),
                        null, Context.NONE);
    }
}
