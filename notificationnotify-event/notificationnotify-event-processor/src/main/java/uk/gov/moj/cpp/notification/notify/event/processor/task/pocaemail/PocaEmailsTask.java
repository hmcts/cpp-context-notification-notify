package uk.gov.moj.cpp.notification.notify.event.processor.task.pocaemail;

import static java.util.Objects.nonNull;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.gov.justice.services.core.annotation.Component.COMMAND_API;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo.executionInfo;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.COMPLETED;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.TaskNames.POCA_EMAIL_TASK;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.ZonedDateTimes;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.fileservice.api.FileServiceException;
import uk.gov.justice.services.fileservice.api.FileStorer;
import uk.gov.moj.cpp.jobstore.api.task.ExecutableTask;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.NotificationNotifyCommandSender;
import uk.gov.moj.cpp.notification.notify.event.processor.PocaApplicationCommandSender;
import uk.gov.moj.cpp.notification.notify.event.processor.error.XmlProcessingException;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.EmailDetail;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.MailServerCredentials;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.EmailHandlerFactory;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.PocaEmailHandler;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import uk.gov.justice.services.messaging.JsonObjects;
import javax.json.JsonObject;
import javax.mail.MessagingException;

import org.slf4j.Logger;

@uk.gov.moj.cpp.jobstore.api.annotation.Task(POCA_EMAIL_TASK)
@ApplicationScoped
public class PocaEmailsTask implements ExecutableTask {

    private static final Logger LOGGER = getLogger(PocaEmailsTask.class);

    private static final String POCA_EMAIL_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String DOCX = "docx";

    @Inject
    UtcClock clock;

    @Inject
    EmailHandlerFactory emailHandlerFactory;

    @Inject
    private PocaApplicationCommandSender pocaApplicationCommandSender;

    @Inject
    private JsonObjectToObjectConverter jsonObjectConverter;

    @Inject
    @ServiceComponent(COMMAND_API)
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
                            } catch (MessagingException | XmlProcessingException e) {
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

    private UUID uploadSingleDocument(EmailDetail emailDetail) {

        int count = 0;
        final int maxRetries = 3;
        UUID pocaFileId;

        final JsonObject metadata = JsonObjects.createObjectBuilder()
                .add("fileName", emailDetail.getFileName())
                .add("createdAt", ZonedDateTimes.toString(new UtcClock().now()))
                .add("mediaType", POCA_EMAIL_CONTENT_TYPE)
                .build();

        while (true) {
            try {
                pocaFileId = fileStorer.store(metadata, emailDetail.getDocumentContent());
                break;
            } catch (FileServiceException ex) {
                LOGGER.error("FAILED - Upload document in the filestore on retry {} of {} error :  {}", count, maxRetries, ex);
                if (++count >= maxRetries) {
                    throw new XmlProcessingException("Unable to process request due to system error. Please try after some time or contact common platform helpdesk.");
                }
            }
        }
        return pocaFileId;
    }
}
