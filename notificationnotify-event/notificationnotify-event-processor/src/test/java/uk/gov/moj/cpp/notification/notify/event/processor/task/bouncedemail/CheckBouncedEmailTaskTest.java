package uk.gov.moj.cpp.notification.notify.event.processor.task.bouncedemail;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.COMPLETED;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.INPROGRESS;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.CHECK_BOUNCED_EMAILS;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.NotificationNotifyCommandSender;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.EmailDetail;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.MailServerCredentials;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.EmailHandler;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.EmailHandlerFactory;

import java.io.IOException;
import java.time.ZonedDateTime;

import javax.json.JsonObject;
import javax.mail.MessagingException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
public class CheckBouncedEmailTaskTest {

    private static final String MAIL_SERVER = "outlook.office365.com";

    @Mock
    private UtcClock utcClock;


    @Mock
    private EmailHandlerFactory emailHandlerFactory;

    @Mock
    private Logger logger;

    @Mock
    private EmailHandler emailHandler;

    @InjectMocks
    private CheckBouncedEmailsTask checkBouncedEmailsTask;

    @Mock
    private NotificationNotifyCommandSender notificationNotifyCommandSender;
    private final ObjectToJsonObjectConverter objectToJsonObjectConverter = new ObjectToJsonObjectConverter();
    @Spy
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;


    @BeforeEach
    public void setup() {
        setField(this.objectToJsonObjectConverter, "mapper", new ObjectMapperProducer().objectMapper());
        setField(this.jsonObjectToObjectConverter, "objectMapper", new ObjectMapperProducer().objectMapper());
    }


    @Test
    public void shouldReturnCheckBouncedEmailTaskAsTheNextTaskWhenBouncedEmailExists() throws MessagingException, IOException {

        final ExecutionInfo checkBouncedEmailExecutionInfo = mock(ExecutionInfo.class);

        when(emailHandlerFactory.createEmailHandler(any())).thenReturn(emailHandler);

        EmailDetail emailDetail = new EmailDetail(null, randomUUID());
        when(emailHandler.fetchEMailDetails()).thenReturn(emailDetail);
        final ZonedDateTime nextStartTime = new UtcClock().now();
        when(utcClock.now()).thenReturn(nextStartTime);
        JsonObject jsonObject = objectToJsonObjectConverter.convert(new MailServerCredentials());
        when(checkBouncedEmailExecutionInfo.getJobData()).thenReturn(jsonObject);
        final ExecutionInfo actualExecutionInfo = checkBouncedEmailsTask.execute(checkBouncedEmailExecutionInfo);

        verify(notificationNotifyCommandSender).processBouncedEmail(emailDetail.getNotificationId());
        verify(emailHandler).moveEmailToArchive(emailDetail);
        assertThat(actualExecutionInfo.getExecutionStatus(), is(INPROGRESS));
        assertThat(actualExecutionInfo.getNextTask(), is(CHECK_BOUNCED_EMAILS.getTaskName()));
        assertThat(actualExecutionInfo.getNextTaskStartTime(), is(nextStartTime));

    }

    @Test
    public void shouldReturnTaskCompletedTaskWhenThereIsNoBouncedEmail() throws MessagingException, IOException {

        final ExecutionInfo checkBouncedEmailExecutionInfo = mock(ExecutionInfo.class);
        when(emailHandler.fetchEMailDetails()).thenReturn(null);
        MailServerCredentials mailServerCredentials = new MailServerCredentials();
        JsonObject jsonObject = objectToJsonObjectConverter.convert(mailServerCredentials);
        when(emailHandlerFactory.createEmailHandler(any(MailServerCredentials.class))).thenReturn(emailHandler);
        when(checkBouncedEmailExecutionInfo.getJobData()).thenReturn(jsonObject);
        final ExecutionInfo actualExecutionInfo = checkBouncedEmailsTask.execute(checkBouncedEmailExecutionInfo);

        assertThat(actualExecutionInfo.getExecutionStatus(), is(COMPLETED));

    }

    @Test
    public void shouldReturnTaskCompletedAndRaiseCommandToRecordFailureWhenThereIsException() throws MessagingException, IOException {

        final ExecutionInfo checkBouncedEmailExecutionInfo = mock(ExecutionInfo.class);
        MailServerCredentials mailServerCredentials = new MailServerCredentials();
        mailServerCredentials.setServer(MAIL_SERVER);
        JsonObject jsonObject = objectToJsonObjectConverter.convert(mailServerCredentials);
        when(emailHandlerFactory.createEmailHandler(any(MailServerCredentials.class))).thenReturn(emailHandler);
        when(checkBouncedEmailExecutionInfo.getJobData()).thenReturn(jsonObject);
        when(emailHandler.fetchEMailDetails()).thenThrow(new IOException("could not connect"));
        final ExecutionInfo actualExecutionInfo = checkBouncedEmailsTask.execute(checkBouncedEmailExecutionInfo);

        assertThat(actualExecutionInfo.getExecutionStatus(), is(COMPLETED));
        verify(notificationNotifyCommandSender).recordCheckBouncedEmailRequestAsFailed(MAIL_SERVER, "could not connect");

    }

}
