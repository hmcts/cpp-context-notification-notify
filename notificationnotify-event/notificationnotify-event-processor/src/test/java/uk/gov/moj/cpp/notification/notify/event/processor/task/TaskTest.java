package uk.gov.moj.cpp.notification.notify.event.processor.task;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_EMAIL_STATUS;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_LETTER_STATUS;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.SEND_EMAIL;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.SEND_LETTER;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.TaskNames.CHECK_EMAIL_STATUS_TASK;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.TaskNames.CHECK_LETTER_STATUS_TASK;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.TaskNames.SEND_EMAIL_TASK;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.TaskNames.SEND_LETTER_TASK;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.fromTaskName;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
public class TaskTest {

    @Test
    public void shouldFindByTaskName() throws Exception {

        assertThat(fromTaskName(SEND_LETTER_TASK), is(SEND_LETTER));
        assertThat(fromTaskName(SEND_EMAIL_TASK), is(SEND_EMAIL));
        assertThat(fromTaskName(CHECK_LETTER_STATUS_TASK), is(CHECK_LETTER_STATUS));
        assertThat(fromTaskName(CHECK_EMAIL_STATUS_TASK), is(CHECK_EMAIL_STATUS));
    }

    @Test
    public void shouldThrowExceptionIfTaskNameNotFound() throws Exception {

        try {
            fromTaskName("something-silly");
            fail();
        } catch (final NotificationTaskException expected) {
            assertThat(expected.getMessage(), is("Failed to find Task with name 'something-silly'"));
        }
    }

    @Test
    public void shouldKnowIfTheTaskIsAnEmailTask() throws Exception {

        assertThat(SEND_EMAIL.isEmailTask(), is(true));
        assertThat(CHECK_EMAIL_STATUS.isEmailTask(), is(true));

        assertThat(SEND_LETTER.isEmailTask(), is(false));
        assertThat(CHECK_LETTER_STATUS.isEmailTask(), is(false));
    }

    @Test
    public void shouldKnowIfTheTaskIsALetter() throws Exception {

        assertThat(SEND_LETTER.isLetterTask(), is(true));
        assertThat(CHECK_LETTER_STATUS.isLetterTask(), is(true));

        assertThat(SEND_EMAIL.isLetterTask(), is(false));
        assertThat(CHECK_EMAIL_STATUS.isLetterTask(), is(false));

    }
}
