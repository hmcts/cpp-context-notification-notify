package uk.gov.moj.cpp.notification.notify.event.processor.task.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import uk.gov.moj.cpp.notification.notify.event.processor.task.Task;

public record NotificationFailedDetails(String errorMessage, Integer statusCode, Task failedTask) {

    @JsonCreator
    public NotificationFailedDetails {
    }
}
