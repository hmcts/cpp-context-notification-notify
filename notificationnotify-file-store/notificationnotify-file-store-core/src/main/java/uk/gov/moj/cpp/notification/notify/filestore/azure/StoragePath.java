package uk.gov.moj.cpp.notification.notify.filestore.azure;

import static java.util.Objects.requireNonNull;

import java.util.UUID;

/**
 * Encapsulates the BYO FileStore v6 path-prefix convention for Azure Blob names.
 *
 * <p>Every blob stored by a CPP service must live under one of three prefixes that reflect
 * the zero-trust RBAC/ABAC access model:
 * <ul>
 *   <li>{@code internal/} — UC1: private files accessed only by the owning service.</li>
 *   <li>{@code published/{topic}/} — UC2: files published for downstream consumers via
 *       a Service Bus message carrying the source blob URI.</li>
 *   <li>{@code inbox/{topic}/} — UC3: doc-gen callback target; written by an external
 *       service (e.g. SDG), read by the owning service.</li>
 * </ul>
 *
 * <p>Access control is enforced by Azure RBAC role assignments scoped to path prefixes —
 * this class only ensures the application writes blobs to the correct prefix.
 *
 * <p>Typical usage: declare one constant and call {@link #blobName(UUID)} to obtain the
 * full blob name:
 * <pre>{@code
 * private static final StoragePath BLOB_PATH = StoragePath.internal();
 *
 * blobContainerClient.getBlobClient(BLOB_PATH.blobName(fileId));
 * }</pre>
 */
public class StoragePath {

    private final String prefix;

    private StoragePath(final String prefix) {
        this.prefix = prefix;
    }

    /**
     * Returns a {@code StoragePath} for UC1 private blobs accessible only by the owning
     * service. Blobs are stored under {@code internal/{fileId}}.
     *
     * @return a {@code StoragePath} with prefix {@code "internal"}
     */
    public static StoragePath internal() {
        return new StoragePath("internal");
    }

    /**
     * Returns a {@code StoragePath} for UC2 blobs published for downstream consumers.
     * Blobs are stored under {@code published/{topic}/{fileId}}.
     *
     * @param topic the topic name that scopes the RBAC reader group, e.g.
     *              {@code "transparency-reports"}
     * @return a {@code StoragePath} with prefix {@code "published/{topic}"}
     */
    public static StoragePath published(final String topic) {
        requireNonNull(topic, "topic must not be null");
        return new StoragePath("published/" + topic);
    }

    /**
     * Returns a {@code StoragePath} for UC3 blobs written into this service's inbox by an
     * external writer (e.g. a doc-gen service). Blobs are stored under
     * {@code inbox/{topic}/{fileId}}.
     *
     * @param topic the topic name that scopes the RBAC writer group, e.g. {@code "sdg-output"}
     * @return a {@code StoragePath} with prefix {@code "inbox/{topic}"}
     */
    public static StoragePath inbox(final String topic) {
        requireNonNull(topic, "topic must not be null");
        return new StoragePath("inbox/" + topic);
    }

    /**
     * Builds the full blob name for the given file ID by appending it to the path prefix.
     *
     * @param fileId the UUID that uniquely identifies the blob within this prefix
     * @return the full blob name, e.g. {@code "internal/a1b2c3d4-..."}
     */
    public String blobName(final UUID fileId) {
        return prefix + "/" + fileId;
    }

    /**
     * Returns the path prefix string, e.g. {@code "internal"} or
     * {@code "published/transparency-reports"}.
     *
     * @return the path prefix without a trailing slash
     */
    public String prefix() {
        return prefix;
    }

    @Override
    public String toString() {
        return prefix;
    }
}
