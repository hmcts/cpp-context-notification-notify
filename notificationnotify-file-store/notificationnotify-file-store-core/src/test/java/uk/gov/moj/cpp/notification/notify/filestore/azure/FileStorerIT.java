package uk.gov.moj.cpp.notification.notify.filestore.azure;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

public class FileStorerIT {

    // Standard Azurite well-known test credential — not a real secret.
    // See: https://learn.microsoft.com/azure/storage/common/storage-use-azurite
    private static final String AZURITE_CONNECTION_STRING =
            "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;" +
            "AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/" +
            "K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://localhost:10000/devstoreaccount1;";

    private static final String CONTAINER_NAME = "filestorer-it-" + UUID.randomUUID().toString().substring(0, 8);
    private static final UUID CORRELATION_ID = UUID.fromString("c0ff1234-0000-0000-0000-000000000001");

    private BlobContainerClient blobContainerClient;
    private FileStorer fileStorer;

    @BeforeEach
    public void setUp() {
        final BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(AZURITE_CONNECTION_STRING)
                .buildClient();
        blobContainerClient = blobServiceClient.getBlobContainerClient(CONTAINER_NAME);
        blobContainerClient.createIfNotExists();

        fileStorer = new FileStorer();
        setField(fileStorer, "blobContainerClient", blobContainerClient);
        setField(fileStorer, "logger", LoggerFactory.getLogger(FileStorer.class));
        final AzureBlobConfiguration azureBlobConfiguration = new AzureBlobConfiguration();
        setField(azureBlobConfiguration, "transferTimeoutSeconds", "300");
        setField(fileStorer, "azureBlobConfiguration", azureBlobConfiguration);
    }

    @AfterEach
    public void tearDown() {
        blobContainerClient.deleteIfExists();
    }

    @Test
    public void shouldStoreContentUnderInternalPrefixAndReturnFileId() {
        final InputStream content = new ByteArrayInputStream("attachment bytes".getBytes());

        final UUID fileId = fileStorer.store(StoragePath.internal(), CORRELATION_ID, "report.pdf", content);

        assertThat(fileId, notNullValue());
        assertThat(blobContainerClient.getBlobClient("internal/" + fileId).exists(), is(true));
    }

    @Test
    public void shouldSetCorrelationIdMetadataOnBlob() {
        final InputStream content = new ByteArrayInputStream("doc bytes".getBytes());

        final UUID fileId = fileStorer.store(StoragePath.internal(), CORRELATION_ID, "letter.pdf", content);

        final String correlationId = blobContainerClient.getBlobClient("internal/" + fileId)
                .getProperties()
                .getMetadata()
                .get("correlation_id");
        assertThat(correlationId, is(CORRELATION_ID.toString()));
    }

    @Test
    public void shouldSetFilenameMetadataOnBlob() {
        final InputStream content = new ByteArrayInputStream("doc bytes".getBytes());

        final UUID fileId = fileStorer.store(StoragePath.internal(), CORRELATION_ID, "letter.pdf", content);

        final String filename = blobContainerClient.getBlobClient("internal/" + fileId)
                .getProperties()
                .getMetadata()
                .get("filename");
        assertThat(filename, is("letter.pdf"));
    }

    @Test
    public void shouldStoreContentUnderPublishedPrefix() {
        final InputStream content = new ByteArrayInputStream("report bytes".getBytes());

        final UUID fileId = fileStorer.store(StoragePath.published("reports"), CORRELATION_ID, "monthly.csv", content);

        assertThat(blobContainerClient.getBlobClient("published/reports/" + fileId).exists(), is(true));
    }

    @Test
    public void shouldAssignDistinctFileIdPerCall() {
        final UUID fileIdOne = fileStorer.store(StoragePath.internal(), CORRELATION_ID, "a.pdf", new ByteArrayInputStream("bytes".getBytes()));
        final UUID fileIdTwo = fileStorer.store(StoragePath.internal(), CORRELATION_ID, "b.pdf", new ByteArrayInputStream("bytes".getBytes()));

        assertThat(fileIdOne.equals(fileIdTwo), is(false));
        final List<BlobItem> blobs = blobContainerClient.listBlobsByHierarchy("internal/").stream().toList();
        assertThat(blobs.size(), is(2));
    }
}
