package uk.gov.moj.cpp.notification.notify.filestore.azure;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

public class FileIngestorIT {

    // Standard Azurite well-known test credential — not a real secret.
    // Same credential used in FileStorerIT / FileRetrieverIT.
    // See: https://learn.microsoft.com/azure/storage/common/storage-use-azurite
    private static final String AZURITE_CONNECTION_STRING =
            "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;"
            + "AccountKey="
            + "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/"
            + "K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://localhost:10000/devstoreaccount1;";

    private static final String SOURCE_CONTAINER = "fileingestor-it-src-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String DEST_CONTAINER   = "fileingestor-it-dst-" + UUID.randomUUID().toString().substring(0, 8);
    private static final UUID   CORRELATION_ID   = UUID.fromString("c0ff1234-0000-0000-0000-000000000002");

    private BlobContainerClient sourceBlobContainerClient;
    private BlobContainerClient destBlobContainerClient;
    private FileIngestor fileIngestor;

    @BeforeEach
    public void setUp() {
        final BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(AZURITE_CONNECTION_STRING)
                .buildClient();
        sourceBlobContainerClient = blobServiceClient.getBlobContainerClient(SOURCE_CONTAINER);
        sourceBlobContainerClient.createIfNotExists();
        destBlobContainerClient = blobServiceClient.getBlobContainerClient(DEST_CONTAINER);
        destBlobContainerClient.createIfNotExists();

        final AzureBlobConfiguration azureBlobConfiguration = new AzureBlobConfiguration();
        setField(azureBlobConfiguration, "connectionTimeoutSeconds", "10");
        setField(azureBlobConfiguration, "responseTimeoutSeconds", "30");
        setField(azureBlobConfiguration, "transferTimeoutSeconds", "300");

        fileIngestor = new FileIngestor();
        setField(fileIngestor, "blobContainerClient", destBlobContainerClient);
        setField(fileIngestor, "logger", LoggerFactory.getLogger(FileIngestor.class));
        setField(fileIngestor, "azureBlobConfiguration", azureBlobConfiguration);
    }

    @AfterEach
    public void tearDown() {
        sourceBlobContainerClient.deleteIfExists();
        destBlobContainerClient.deleteIfExists();
    }

    @Test
    public void shouldCopyBlobFromSourceUriToInternalPath() {
        final UUID fileId = UUID.randomUUID();
        final BlobClient sourceBlobClient = uploadSourceBlob(fileId, "hello world".getBytes());
        final URI sourceUri = URI.create(sourceBlobClient.getBlobUrl());

        fileIngestor.ingest(StoragePath.internal(), fileId, CORRELATION_ID, "report.csv", sourceUri);

        assertThat(destBlobContainerClient.getBlobClient("internal/" + fileId).exists(), is(true));
    }

    @Test
    public void shouldSetCorrelationIdMetadataOnIngestedBlob() {
        final UUID fileId = UUID.randomUUID();
        final BlobClient sourceBlobClient = uploadSourceBlob(fileId, "content bytes".getBytes());
        final URI sourceUri = URI.create(sourceBlobClient.getBlobUrl());

        fileIngestor.ingest(StoragePath.internal(), fileId, CORRELATION_ID, "report.csv", sourceUri);

        final Map<String, String> metadata = destBlobContainerClient.getBlobClient("internal/" + fileId)
                .getProperties()
                .getMetadata();
        assertThat(metadata.get("correlation_id"), is(CORRELATION_ID.toString()));
    }

    @Test
    public void shouldSetFilenameMetadataOnIngestedBlob() {
        final UUID fileId = UUID.randomUUID();
        final BlobClient sourceBlobClient = uploadSourceBlob(fileId, "content bytes".getBytes());
        final URI sourceUri = URI.create(sourceBlobClient.getBlobUrl());

        fileIngestor.ingest(StoragePath.internal(), fileId, CORRELATION_ID, "letter.docx", sourceUri);

        final Map<String, String> metadata = destBlobContainerClient.getBlobClient("internal/" + fileId)
                .getProperties()
                .getMetadata();
        assertThat(metadata.get("filename"), is("letter.docx"));
    }

    @Test
    public void shouldCopyBlobContentFromSourceToDestination() {
        final UUID fileId = UUID.randomUUID();
        final byte[] originalContent = "original document content".getBytes();
        final BlobClient sourceBlobClient = uploadSourceBlob(fileId, originalContent);
        final URI sourceUri = URI.create(sourceBlobClient.getBlobUrl());

        fileIngestor.ingest(StoragePath.internal(), fileId, CORRELATION_ID, "doc.pdf", sourceUri);

        final byte[] copiedContent = destBlobContainerClient.getBlobClient("internal/" + fileId)
                .downloadContent()
                .toBytes();
        assertThat(copiedContent, is(originalContent));
    }

    private BlobClient uploadSourceBlob(final UUID fileId, final byte[] content) {
        final BlobClient blobClient = sourceBlobContainerClient.getBlobClient("source/" + fileId);
        blobClient.upload(new java.io.ByteArrayInputStream(content), content.length, true);
        return blobClient;
    }
}
