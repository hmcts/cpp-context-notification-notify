package uk.gov.moj.cpp.notification.notify.event.processor.task.pocaemail;

import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo.executionInfo;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.COMPLETED;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.TaskNames.POCA_EMAIL_TASK;

import com.azure.storage.blob.models.BlobStorageException;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.moj.cpp.notification.notify.filestore.azure.FileStorer;
import uk.gov.moj.cpp.notification.notify.filestore.azure.StoragePath;
import uk.gov.moj.cpp.jobstore.api.task.ExecutableTask;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.NotificationNotifyCommandSender;
import uk.gov.moj.cpp.notification.notify.event.processor.PocaApplicationCommandSender;
import uk.gov.moj.cpp.notification.notify.event.processor.error.DocumentUploadException;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.EmailDetail;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.MailServerCredentials;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.EmailHandlerFactory;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.PocaEmailHandler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.mail.MessagingException;

import org.slf4j.Logger;

@uk.gov.moj.cpp.jobstore.api.annotation.Task(POCA_EMAIL_TASK)
@ApplicationScoped
public class PocaEmailsTask implements ExecutableTask {

    private static final Logger LOGGER = getLogger(PocaEmailsTask.class);

    private static final String DOCX = "docx";

    private static final StoragePath BLOB_PATH = StoragePath.internal();

    @Inject
    EmailHandlerFactory emailHandlerFactory;

    @Inject
    private PocaApplicationCommandSender pocaApplicationCommandSender;

    @Inject
    private JsonObjectToObjectConverter jsonObjectConverter;

    @Inject
    private FileStorer fileStorer;

    @Inject
    private NotificationNotifyCommandSender notificationNotifyCommandSender;

    @Override
    @SuppressWarnings({"squid:S1166", "squid:S134"})
    public ExecutionInfo execute(final ExecutionInfo executionInfo) {

        final MailServerCredentials mailServerCredentials = jsonObjectConverter.convert(executionInfo.getJobData(), MailServerCredentials.class);

        try (final PocaEmailHandler emailHandler = emailHandlerFactory.createPocaEmailHandler(mailServerCredentials)) {
            final List<EmailDetail> emailDetails = emailHandler.fetchPocaEmailDetails();

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Executing PocaEmailTask {}", emailDetails);
            }
            if (nonNull(emailDetails)) {
                emailDetails.stream()
                        .filter(Objects::nonNull)
                        .forEach(emailDetail -> {
                            try {
                                if (nonNull(emailDetail.getDocumentContent()) && emailDetail.getFileName().endsWith(DOCX)) {
                                    pocaApplicationCommandSender.processPocaEmail(uploadSingleDocument(emailDetail), emailDetail.getPocaMailId(), emailDetail.getSenderEmail(), emailDetail.getSubject());
                                }
                                emailHandler.deleteEmail(emailDetail);
                            } catch (MessagingException | DocumentUploadException e) {
                                LOGGER.error("PocaEmailsTask failed to receive email", e);
                                notificationNotifyCommandSender.recordCheckPocaEmailRequestAsFailed(mailServerCredentials.getServer(), e.getMessage());
                            }
                        });
            }
        } catch (MessagingException | IOException  e) {
            LOGGER.error("PocaEmailsTask Failed to process file", e);
        }

        return executionInfo()
                .withExecutionStatus(COMPLETED)
                .build();
    }

    private UUID uploadSingleDocument(final EmailDetail emailDetail) {
        final byte[] documentBytes;
        try {
            documentBytes = emailDetail.getDocumentContent().readAllBytes();
        } catch (final IOException e) {
            throw new DocumentUploadException("Unable to read document content from email attachment", e);
        }

        final int maxRetries = 3;
        final UUID correlationId = randomUUID();
        BlobStorageException lastException = null;

        for (int count = 0; count < maxRetries; count++) {
            try {
                return fileStorer.store(BLOB_PATH, correlationId, emailDetail.getFileName(), new ByteArrayInputStream(documentBytes));
            } catch (final BlobStorageException ex) {
                LOGGER.warn("Upload to blob storage failed on attempt {} of {} for filename='{}': {}",
                        count + 1, maxRetries, emailDetail.getFileName(), ex.getMessage());
                lastException = ex;
            }
        }
        throw new DocumentUploadException(
                "Failed to upload document '" + emailDetail.getFileName() + "' to blob storage after " + maxRetries + " attempts",
                lastException);
    }
}
