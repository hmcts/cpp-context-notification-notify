package uk.gov.moj.cpp.notification.notify.event.processor.task.email;

import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_EMAIL_STATUS;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.TaskNames.CHECK_EMAIL_STATUS_TASK;

import java.util.List;
import java.util.Optional;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.moj.cpp.jobstore.api.task.ExecutableTask;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.NotificationResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.StatusResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.retry.RetryService;
import uk.gov.moj.cpp.notification.notify.event.processor.sender.NotificationStatusChecker;
import uk.gov.moj.cpp.notification.notify.event.processor.task.Task;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExternalIdentifierJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.task.processors.EmailStatusResponseProcessor;
import uk.gov.moj.cpp.notification.notify.event.processor.task.processors.StatusErrorResponseProcessor;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

@uk.gov.moj.cpp.jobstore.api.annotation.Task(CHECK_EMAIL_STATUS_TASK)
@ApplicationScoped
public class CheckEmailStatusTask implements ExecutableTask {

    @Inject
    private JsonObjectToObjectConverter jsonObjectConverter;

    @Inject
    private NotificationStatusChecker notificationStatusChecker;

    @Inject
    private EmailStatusResponseProcessor emailStatusResponseProcessor;

    @Inject
    private StatusErrorResponseProcessor statusErrorResponseProcessor;

    @SuppressWarnings({"squid:S1312"})//suppressing Sonar warning of logger not being static final
    @Inject
    private Logger logger;

    @Inject
    private RetryService retryService;

    @Override
    public Optional<List<Long>> getRetryDurationsInSecs() {
        return retryService.getRetryDurationsInSecs(CHECK_EMAIL_STATUS);
    }

    @Override
    public ExecutionInfo execute(final ExecutionInfo checkStatusExecutionInfo) {

        final Task task = CHECK_EMAIL_STATUS;

        final ExternalIdentifierJobState externalIdentifierJobState = jsonObjectConverter.convert(
                checkStatusExecutionInfo.getJobData(),
                ExternalIdentifierJobState.class);

        logger.debug("Executing CHECK EMAIL STATUS task {}", externalIdentifierJobState.getNotificationId());

        final NotificationResponse notificationResponse = notificationStatusChecker.checkStatus(externalIdentifierJobState);

        if (notificationResponse.isSuccessful()) {
            return emailStatusResponseProcessor.process(
                    externalIdentifierJobState,
                    (StatusResponse) notificationResponse,
                    task);
        }

        return statusErrorResponseProcessor.process(
                externalIdentifierJobState,
                (ErrorResponse) notificationResponse,
                task);
    }

}
