package uk.gov.moj.cpp.notification.notify.event.processor.task.bouncedemail;

import static java.util.Objects.nonNull;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo.executionInfo;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.COMPLETED;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.INPROGRESS;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_BOUNCED_EMAILS;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.TaskNames.CHECK_BOUNCED_EMAILS_TASK;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.moj.cpp.jobstore.api.task.ExecutableTask;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.NotificationNotifyCommandSender;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.EmailDetail;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.MailServerCredentials;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.EmailHandler;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.EmailHandlerFactory;

import java.io.IOException;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.mail.MessagingException;

import org.slf4j.Logger;

@uk.gov.moj.cpp.jobstore.api.annotation.Task(CHECK_BOUNCED_EMAILS_TASK)
@ApplicationScoped
public class CheckBouncedEmailsTask implements ExecutableTask {

    private static final Logger LOGGER = getLogger(CheckBouncedEmailsTask.class);
    @Inject
    UtcClock clock;
    @Inject
    EmailHandlerFactory emailHandlerFactory;
    @Inject
    private NotificationNotifyCommandSender notificationNotifyCommandSender;

    @Inject
    private JsonObjectToObjectConverter jsonObjectConverter;

    @Override
    @SuppressWarnings({"squid:S1166"})
    public ExecutionInfo execute(final ExecutionInfo executionInfo) {

        final MailServerCredentials mailServerCredentials = jsonObjectConverter.convert(executionInfo.getJobData(), MailServerCredentials.class);

        try (final EmailHandler emailHandler = emailHandlerFactory.createEmailHandler(mailServerCredentials)) {
            final EmailDetail emailDetail = emailHandler.fetchEMailDetails();

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Executing checkBouncedEmailTask {}", emailDetail);
            }

            if (nonNull(emailDetail)) {
                if (nonNull(emailDetail.getNotificationId())) {
                    notificationNotifyCommandSender.processBouncedEmail(emailDetail.getNotificationId());
                }
                emailHandler.moveEmailToArchive(emailDetail);
                return executionInfo()
                        .withJobData(executionInfo.getJobData())
                        .withNextTask(CHECK_BOUNCED_EMAILS.getTaskName())
                        .withNextTaskStartTime(clock.now())
                        .withExecutionStatus(INPROGRESS)
                        .build();
            }

        } catch (MessagingException | IOException e) {
            notificationNotifyCommandSender.recordCheckBouncedEmailRequestAsFailed(mailServerCredentials.getServer(), e.getMessage());
        }

        return executionInfo()
                .withExecutionStatus(COMPLETED)
                .build();
    }

}
