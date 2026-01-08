package uk.gov.moj.cpp.notification.notify.event.processor.task;

import static java.lang.String.format;
import static java.util.Arrays.stream;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.TaskNames.CHECK_ACCEPTED_LETTER_STATUS_TASK;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.TaskNames.CHECK_BOUNCED_EMAILS_TASK;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.TaskNames.CHECK_EMAIL_STATUS_TASK;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.TaskNames.CHECK_LETTER_STATUS_TASK;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.TaskNames.NOTIFICATION_FAILED_TASK;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.TaskNames.POCA_EMAIL_TASK;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.TaskNames.SEND_EMAIL_TASK;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.TaskNames.SEND_LETTER_TASK;

public enum Task {

    SEND_EMAIL(SEND_EMAIL_TASK),
    SEND_LETTER(SEND_LETTER_TASK),
    CHECK_EMAIL_STATUS(CHECK_EMAIL_STATUS_TASK),
    CHECK_LETTER_STATUS(CHECK_LETTER_STATUS_TASK),
    
    CHECK_ACCEPTED_LETTER_STATUS(CHECK_ACCEPTED_LETTER_STATUS_TASK),
    CHECK_BOUNCED_EMAILS(CHECK_BOUNCED_EMAILS_TASK),
    NOTIFICATION_FAILED(NOTIFICATION_FAILED_TASK),
    POCA_EMAIL(POCA_EMAIL_TASK);

    private final String taskName;

    Task(final String taskName) {
        this.taskName = taskName;
    }

    public static Task fromTaskName(final String taskName) {

        return stream(values())
                .filter(task -> task.taskName.equals(taskName))
                .findFirst()
                .orElseThrow(() -> new NotificationTaskException(format("Failed to find Task with name '%s'", taskName)));
    }

    public String getTaskName() {
        return taskName;
    }

    public boolean isLetterTask() {
        return this == SEND_LETTER || this == CHECK_LETTER_STATUS;
    }

    public boolean isEmailTask() {
        return this == SEND_EMAIL || this == CHECK_EMAIL_STATUS;
    }

    public static class TaskNames {
        public static final String SEND_EMAIL_TASK = "send-email";
        public static final String SEND_LETTER_TASK = "send-letter";
        public static final String CHECK_EMAIL_STATUS_TASK = "check-status";
        public static final String CHECK_LETTER_STATUS_TASK = "check-letter-status";
        public static final String CHECK_ACCEPTED_LETTER_STATUS_TASK = "check-accepted-letter-status";
        public static final String CHECK_BOUNCED_EMAILS_TASK = "check-bounced-emails";
        public static final String POCA_EMAIL_TASK = "poca-email";
        public static final String NOTIFICATION_FAILED_TASK = "notification-failed";

        private TaskNames() {
        }
    }
}
