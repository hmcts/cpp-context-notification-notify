package uk.gov.moj.cpp.notification.notify.command.handler;

import static uk.gov.justice.services.core.annotation.Component.COMMAND_HANDLER;

import uk.gov.justice.json.schemas.domains.notificationnotify.IngestFile;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.notification.notify.filestore.azure.FileIngestor;
import uk.gov.moj.cpp.notification.notify.filestore.azure.StoragePath;

import java.net.URI;

import javax.inject.Inject;

@ServiceComponent(COMMAND_HANDLER)
public class IngestFileCommandHandler {

    private static final StoragePath BLOB_PATH = StoragePath.internal();

    @Inject
    private FileIngestor fileIngestor;

    @Handles("notificationnotify.command.ingest-file")
    public void ingestFile(final Envelope<IngestFile> envelope) {
        final IngestFile ingestFile = envelope.payload();
        fileIngestor.ingest(
                BLOB_PATH,
                ingestFile.getFileId(),
                ingestFile.getCorrelationId(),
                ingestFile.getFilename(),
                URI.create(ingestFile.getSourceUri()));
    }
}
