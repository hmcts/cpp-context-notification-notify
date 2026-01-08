package uk.gov.moj.cpp.notification.notify.event.processor.task.handlers;

import javax.inject.Inject;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExternalIdentifierJobState;

import static uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus.ACCEPTED;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_ACCEPTED_LETTER_STATUS;

public class LetterAcceptedRetryHandler implements LetterAcceptedHandler {

    @Inject
    private RetryHandler retryHandler;

    @Override
    public ExecutionInfo handle(final ExternalIdentifierJobState externalIdentifierJobState) {
        return retryHandler.handle(externalIdentifierJobState.getNotificationId(),
                CHECK_ACCEPTED_LETTER_STATUS,
                ACCEPTED);
    }
}
