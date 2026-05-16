package uk.gov.moj.cpp.notification.notify.filestore.test;

import static com.azure.core.util.BinaryData.fromBytes;
import static java.util.Map.of;
import static java.util.Optional.empty;
import static java.util.UUID.randomUUID;

import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.options.BlobParallelUploadOptions;

import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.UUID;

/**
 * Test utility for pre-populating Azure Blob Storage containers in integration tests.
 *
 * <p>Provides upload, download, exists, and delete operations against the path-prefix
 * convention used by the BYO FileStore design (inbox/&lt;topic&gt;,
 * published/&lt;topic&gt;, internal/).
 *
 * <p>Intended to be consumed in {@code test} scope by any context IT module that needs
 * to seed blobs before triggering event processor flows. Use via the static factory:
 *
 * <pre>{@code
 * final BlobStoreTestHelper helper =
 *         BlobStoreTestHelper.forConnectionStringAndContainer(connectionString, containerName);
 *
 * final UUID fileId = helper.upload("inbox/notification-templates", "template.pdf", pdfBytes);
 * }</pre>
 */
public class BlobStoreTestHelper {

    private final BlobContainerClient blobContainerClient;
    private final String containerName;

    BlobStoreTestHelper(final BlobContainerClient blobContainerClient, final String containerName) {
        this.blobContainerClient = blobContainerClient;
        this.containerName = containerName;
    }

    /**
     * Creates a helper connected to the given container. The container is created if it
     * does not already exist.
     *
     * @param connectionString Azure Blob Storage connection string (Azurite or real account)
     * @param containerName    target container
     * @return a ready-to-use {@code BlobStoreTestHelper}
     */
    public static BlobStoreTestHelper forConnectionStringAndContainer(final String connectionString,
                                                                      final String containerName) {
        final BlobContainerClient blobContainerClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient()
                .getBlobContainerClient(containerName);
        blobContainerClient.createIfNotExists();
        return new BlobStoreTestHelper(blobContainerClient, containerName);
    }

    /**
     * Uploads {@code content} to {@code pathPrefix/&lt;randomUUID&gt;}, sets {@code filename}
     * and {@code correlation_id} metadata per the BYOFS-1.3 convention, and returns the
     * generated file ID (the blob's UUID name under the prefix).
     *
     * @param pathPrefix path prefix, e.g. {@code "inbox/notification-templates"}
     * @param filename   value stored in blob metadata under the {@code filename} key
     * @param content    raw bytes to upload
     * @return the UUID assigned as the blob name under {@code pathPrefix}
     */
    public UUID upload(final String pathPrefix, final String filename, final byte[] content) {
        final UUID fileId = randomUUID();
        blobContainerClient.getBlobClient(pathPrefix + "/" + fileId)
                .uploadWithResponse(new BlobParallelUploadOptions(fromBytes(content))
                        .setMetadata(of(
                                "filename", filename,
                                "correlation_id", fileId.toString())),
                        null, Context.NONE);
        return fileId;
    }

    /**
     * Downloads the content of the blob at {@code pathPrefix/fileId}.
     *
     * @param pathPrefix path prefix, e.g. {@code "inbox/notification-templates"}
     * @param fileId     the blob's UUID name under the prefix
     * @return the raw bytes, or {@link Optional#empty()} if the blob does not exist
     */
    public Optional<byte[]> download(final String pathPrefix, final UUID fileId) {
        final BlobClient blobClient = blobContainerClient.getBlobClient(pathPrefix + "/" + fileId);
        if (!blobClient.exists()) {
            return empty();
        }
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        blobClient.downloadStreamWithResponse(outputStream, new BlobRange(0, 1_000_000_000L),
                null, null, false, null, null);
        return Optional.of(outputStream.toByteArray());
    }

    /**
     * Returns {@code true} if the blob at {@code pathPrefix/fileId} exists.
     *
     * @param pathPrefix path prefix, e.g. {@code "internal"}
     * @param fileId     the blob's UUID name under the prefix
     * @return {@code true} if the blob exists, {@code false} otherwise
     */
    public boolean exists(final String pathPrefix, final UUID fileId) {
        return blobContainerClient.getBlobClient(pathPrefix + "/" + fileId).exists();
    }

    /**
     * Deletes the blob at {@code pathPrefix/fileId}. No-op if the blob does not exist.
     *
     * @param pathPrefix path prefix
     * @param fileId     the blob's UUID name under the prefix
     */
    public void delete(final String pathPrefix, final UUID fileId) {
        blobContainerClient.getBlobClient(pathPrefix + "/" + fileId).deleteIfExists();
    }

    /**
     * Returns the name of the container this helper is connected to.
     *
     * @return the container name supplied to {@link #forConnectionStringAndContainer}
     */
    public String containerName() {
        return containerName;
    }
}
