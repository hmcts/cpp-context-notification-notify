package uk.gov.moj.cpp.notification.notify.filestore.azure;

import static java.util.Arrays.copyOfRange;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobRange;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies cross-container blob streaming — the UC2.1 stream-to-sink pattern.
 *
 * Simulates the mi-reportdata → notification-notify pilot: a report blob is uploaded
 * to a "producer" container, then streamed back by a separate client representing the
 * receiver. Asserts byte-for-byte fidelity and that no copy lands in the receiver's
 * container.
 *
 * Runs against Azurite. See streaming.md Pattern 3 for the production equivalent
 * using DefaultAzureCredential + BYOFS-2.1 RBAC grant.
 */
public class LiveReportEmailDeliveryIT {

    // Standard Azurite well-known test credential — not a real secret.
    // Same credential used in FileStorerIT / FileRetrieverIT / FileIngestorIT.
    // See: https://learn.microsoft.com/azure/storage/common/storage-use-azurite
    private static final String AZURITE_CONNECTION_STRING =
            "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;"
            + "AccountKey="
            + "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/"
            + "K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://localhost:10000/devstoreaccount1;";

    private static final String PRODUCER_CONTAINER =
            "live-report-it-producer-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String RECEIVER_CONTAINER =
            "live-report-it-receiver-" + UUID.randomUUID().toString().substring(0, 8);
    private static final UUID CORRELATION_ID = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000099");

    private BlobServiceClient blobServiceClient;
    private BlobContainerClient producerContainerClient;
    private BlobContainerClient receiverContainerClient;

    @BeforeEach
    public void setUp() {
        blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(AZURITE_CONNECTION_STRING)
                .buildClient();
        producerContainerClient = blobServiceClient.getBlobContainerClient(PRODUCER_CONTAINER);
        producerContainerClient.createIfNotExists();
        receiverContainerClient = blobServiceClient.getBlobContainerClient(RECEIVER_CONTAINER);
        receiverContainerClient.createIfNotExists();
    }

    @AfterEach
    public void tearDown() {
        producerContainerClient.deleteIfExists();
        receiverContainerClient.deleteIfExists();
    }

    @Test
    public void shouldStreamBytesFromProducerContainerWithoutCopyingToReceiverContainer() {
        final byte[] reportBytes = "report-content-row1,row2,row3".getBytes();
        final String blobUri = uploadToProducerContainer(reportBytes);

        final byte[] streamedBytes = streamCrossContainer(blobUri);

        assertThat(streamedBytes, is(reportBytes));
        assertThat("no blob should land in receiver container",
                receiverContainerClient.listBlobs().iterator().hasNext(), is(false));
    }

    @Test
    public void shouldStreamCorrectBytesForLargerPayload() {
        final byte[] reportBytes = buildReportBytes(512);
        final String blobUri = uploadToProducerContainer(reportBytes);

        final byte[] streamedBytes = streamCrossContainer(blobUri);

        assertThat(streamedBytes, is(reportBytes));
    }

    @Test
    public void shouldStreamFromPublishedPrefixPath() {
        final byte[] reportBytes = "warrant-report-data".getBytes();
        final UUID fileId = UUID.randomUUID();
        final String blobName = "published/live-reports/" + fileId;
        final BlobClient blobClient = producerContainerClient.getBlobClient(blobName);
        blobClient.upload(new ByteArrayInputStream(reportBytes), reportBytes.length, true);
        final String blobUri = blobClient.getBlobUrl();

        final byte[] streamedBytes = streamCrossContainer(blobUri);

        assertThat(streamedBytes, is(reportBytes));
    }

    @Test
    public void shouldResolveContainerAndBlobNameFromCanonicalUri() {
        final byte[] reportBytes = "canonical-uri-test".getBytes();
        final String blobUri = uploadToProducerContainer(reportBytes);

        final URI uri = URI.create(blobUri);
        final String[] segments = uri.getPath().split("/");
        final String resolvedContainer = segments[2];

        assertThat(resolvedContainer, is(PRODUCER_CONTAINER));
        final String resolvedBlobName = String.join("/", copyOfRange(segments, 3, segments.length));
        final BlobClient resolvedClient = blobServiceClient
                .getBlobContainerClient(resolvedContainer)
                .getBlobClient(resolvedBlobName);
        assertThat(resolvedClient.exists(), is(true));
    }

    private String uploadToProducerContainer(final byte[] content) {
        final UUID fileId = UUID.randomUUID();
        final String blobName = "published/live-reports/" + fileId + "/report-" + CORRELATION_ID + ".xlsx";
        final BlobClient blobClient = producerContainerClient.getBlobClient(blobName);
        blobClient.upload(new ByteArrayInputStream(content), content.length, true);
        return blobClient.getBlobUrl();
    }

    /**
     * Mirrors BlobFileEmailSender.buildBlobClientFromConnectionString.
     * Azurite path: /devstoreaccount1/{container}/{blobPath...}
     * segments[0]="" segments[1]="devstoreaccount1" segments[2]={container} segments[3+]={blobPath}
     */
    private byte[] streamCrossContainer(final String canonicalBlobUri) {
        final URI uri = URI.create(canonicalBlobUri);
        final String[] segments = uri.getPath().split("/");
        final String containerName = segments[2];
        final String blobName = String.join("/", copyOfRange(segments, 3, segments.length));
        final BlobClient blobClient = blobServiceClient
                .getBlobContainerClient(containerName)
                .getBlobClient(blobName);

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        blobClient.downloadStreamWithResponse(
                outputStream,
                new BlobRange(0, 1_000_000_000L),
                null, null, false, null, null);
        return outputStream.toByteArray();
    }

    private static byte[] buildReportBytes(final int rows) {
        final StringBuilder content = new StringBuilder("col1,col2,col3\n");
        for (int i = 0; i < rows; i++) {
            content.append("value").append(i).append(",")
                    .append("value").append(i * 2).append(",")
                    .append("value").append(i * 3).append("\n");
        }
        return content.toString().getBytes();
    }
}
