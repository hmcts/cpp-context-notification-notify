package uk.gov.moj.cpp.notification.notify.event.processor.task.handlers;

import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExternalIdentifierJobState;

public interface LetterAcceptedHandler {

    ExecutionInfo handle(final ExternalIdentifierJobState externalIdentifierJobState);
}
