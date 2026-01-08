package uk.gov.moj.cpp.notification.notify.event.processor.task.letter;

import javax.inject.Inject;
import org.slf4j.Logger;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.NotificationResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.StatusResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.sender.NotificationStatusChecker;
import uk.gov.moj.cpp.notification.notify.event.processor.task.Task;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExternalIdentifierJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.LetterAcceptedHandler;
import uk.gov.moj.cpp.notification.notify.event.processor.task.processors.LetterStatusResponseProcessor;
import uk.gov.moj.cpp.notification.notify.event.processor.task.processors.StatusErrorResponseProcessor;

public class CheckLetterStatusHandler {

    @SuppressWarnings({"squid:S1312"})//suppressing Sonar warning of logger not being static final
    @Inject
    private Logger logger;

    @Inject
    private JsonObjectToObjectConverter jsonObjectConverter;

    @Inject
    private NotificationStatusChecker notificationStatusChecker;

    @Inject
    private LetterStatusResponseProcessor letterStatusResponseProcessor;

    @Inject
    private StatusErrorResponseProcessor statusErrorResponseProcessor;


    public ExecutionInfo handle(final ExecutionInfo checkStatusExecutionInfo,
                                final LetterAcceptedHandler letterAcceptedHandler,
                                final Task task) {

        final ExternalIdentifierJobState externalIdentifierJobState = jsonObjectConverter.convert(
                checkStatusExecutionInfo.getJobData(),
                ExternalIdentifierJobState.class);

        logger.debug(String.format("Executing %s task {}", task.name()), externalIdentifierJobState.getNotificationId());

        final NotificationResponse notificationResponse = notificationStatusChecker.checkStatus(externalIdentifierJobState);

        if (notificationResponse.isSuccessful()) {
            return letterStatusResponseProcessor.process(
                    externalIdentifierJobState,
                    (StatusResponse) notificationResponse,
                    task, letterAcceptedHandler);
        }

        return statusErrorResponseProcessor.process(
                externalIdentifierJobState,
                (ErrorResponse) notificationResponse,
                task);
    }
}
