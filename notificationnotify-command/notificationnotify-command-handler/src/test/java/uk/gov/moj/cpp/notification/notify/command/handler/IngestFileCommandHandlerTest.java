package uk.gov.moj.cpp.notification.notify.command.handler;

import static java.util.UUID.randomUUID;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.justice.json.schemas.domains.notificationnotify.IngestFile;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.notification.notify.filestore.azure.FileIngestor;
import uk.gov.moj.cpp.notification.notify.filestore.azure.StoragePath;

import java.net.URI;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class IngestFileCommandHandlerTest {

    @Mock
    private FileIngestor fileIngestor;

    @Mock
    private Envelope<IngestFile> envelope;

    @Mock
    private IngestFile ingestFile;

    @InjectMocks
    private IngestFileCommandHandler ingestFileCommandHandler;

    @Test
    public void shouldDelegateToFileIngestorWithCorrectArguments() {
        final UUID fileId = randomUUID();
        final UUID correlationId = randomUUID();
        final String filename = "live_report.csv";
        final String sourceUri = "https://storage.blob.core.windows.net/mi-reportdata/published/" + fileId + "?sp=r&sig=test";

        when(envelope.payload()).thenReturn(ingestFile);
        when(ingestFile.getFileId()).thenReturn(fileId);
        when(ingestFile.getCorrelationId()).thenReturn(correlationId);
        when(ingestFile.getFilename()).thenReturn(filename);
        when(ingestFile.getSourceUri()).thenReturn(sourceUri);

        ingestFileCommandHandler.ingestFile(envelope);

        verify(fileIngestor).ingest(
                isA(StoragePath.class),
                eq(fileId),
                eq(correlationId),
                eq(filename),
                eq(URI.create(sourceUri)));
    }
}
