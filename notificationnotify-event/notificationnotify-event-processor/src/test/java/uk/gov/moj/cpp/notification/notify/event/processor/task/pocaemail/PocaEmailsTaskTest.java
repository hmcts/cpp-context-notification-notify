package uk.gov.moj.cpp.notification.notify.event.processor.task.pocaemail;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.COMPLETED;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.domain.EmailDetail.emailDetails;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.fileservice.api.FileServiceException;
import uk.gov.justice.services.fileservice.api.FileStorer;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.NotificationNotifyCommandSender;
import uk.gov.moj.cpp.notification.notify.event.processor.PocaApplicationCommandSender;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.EmailDetail;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.MailServerCredentials;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.EmailHandlerFactory;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.PocaEmailHandler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.json.JsonObject;
import javax.mail.MessagingException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PocaEmailsTaskTest {

    private static final String MAIL_SERVER = "outlook.office365.com";
    public static final String MESSAGE = "Unable to process request due to system error. Please try after some time or contact common platform helpdesk.";

    @Mock
    private UtcClock utcClock;

    @Mock
    private EmailHandlerFactory emailHandlerFactory;

    @Mock
    private PocaEmailHandler pocaEmailHandler;

    @InjectMocks
    private PocaEmailsTask pocaEmailsTask;

    @Mock
    private PocaApplicationCommandSender pocaApplicationCommandSender;

    private final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();
    private final ObjectToJsonObjectConverter objectToJsonObjectConverter = new ObjectToJsonObjectConverter(objectMapper);

    @Spy
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;

    @Mock
    private FileStorer fileStorer;

    @Mock
    private NotificationNotifyCommandSender notificationNotifyCommandSender;

    @BeforeEach
    public void setup() {
        setField(this.objectToJsonObjectConverter, "mapper", new ObjectMapperProducer().objectMapper());
        setField(this.jsonObjectToObjectConverter, "objectMapper", new ObjectMapperProducer().objectMapper());
    }

    @Test
    public void shouldReturnPocaEmailsTaskAsTheNextTaskWhenPocaEmailsExists() throws MessagingException, IOException, FileServiceException {

        final UUID pocaFileIdMailOne = randomUUID();
        final UUID pocaFileIdMailTwo = randomUUID();
        final UUID pocaMailId = new UUID(new Date().getTime(), 1L);

        final ExecutionInfo executionInfo = mock(ExecutionInfo.class);

        when(emailHandlerFactory.createPocaEmailHandler(any())).thenReturn(pocaEmailHandler);

        byte[] initialArray = {0, 1, 2};
        List<EmailDetail> emailDetails = Arrays.asList(getUserOneEmailDetail(pocaMailId, initialArray), getUserTwoEmailDetail(pocaMailId, initialArray));

        when(pocaEmailHandler.fetchPocaEmailDetails()).thenReturn(emailDetails);

        JsonObject jsonObject = objectToJsonObjectConverter.convert(new MailServerCredentials());
        when(executionInfo.getJobData()).thenReturn(jsonObject);

        when(fileStorer.store(any(), any())).thenReturn(pocaFileIdMailOne).thenReturn(pocaFileIdMailTwo);

        final ExecutionInfo actualExecutionInfo = pocaEmailsTask.execute(executionInfo);

        assertPocaEmailDetails(pocaFileIdMailOne, pocaMailId, emailDetails.get(0), actualExecutionInfo);
        assertPocaEmailDetails(pocaFileIdMailTwo, pocaMailId, emailDetails.get(1), actualExecutionInfo);
    }

    @Test
    public void shouldReturnTaskCompletedAndRaiseCommandToRecordFailureWhenFailedToUploadFileException() throws MessagingException, IOException, FileServiceException {

        final UUID pocaFileId = randomUUID();
        final UUID pocaMailId = new UUID(new Date().getTime(), 1L);

        final ExecutionInfo executionInfo = mock(ExecutionInfo.class);

        when(emailHandlerFactory.createPocaEmailHandler(any())).thenReturn(pocaEmailHandler);

        byte[] initialArray = {0, 1, 2};
        List<EmailDetail> emailDetails = Arrays.asList(getUserOneEmailDetail(pocaMailId, initialArray), getUserTwoEmailDetail(pocaMailId, initialArray));

        when(pocaEmailHandler.fetchPocaEmailDetails()).thenReturn(emailDetails);

        JsonObject jsonObject = objectToJsonObjectConverter.convert(new MailServerCredentials());
        when(executionInfo.getJobData()).thenReturn(jsonObject);

        when(fileStorer.store(any(), any())).thenThrow(new FileServiceException(MESSAGE))
                .thenThrow(new FileServiceException(MESSAGE))
                .thenThrow(new FileServiceException(MESSAGE))
                .thenReturn(pocaFileId);

        final ExecutionInfo actualExecutionInfo = pocaEmailsTask.execute(executionInfo);

        assertThat(actualExecutionInfo.getExecutionStatus(), is(COMPLETED));

        verify(notificationNotifyCommandSender).recordCheckPocaEmailRequestAsFailed(any(), any());

    }


    @Test
    public void shouldReturnTaskCompletedAndRaiseCommandToRecordFailureWhenFailedToProcessAnyEmail() throws MessagingException, IOException, FileServiceException {

        final UUID pocaFileId = randomUUID();
        final UUID pocaMailId = new UUID(new Date().getTime(), 1L);

        final ExecutionInfo executionInfo = mock(ExecutionInfo.class);

        when(emailHandlerFactory.createPocaEmailHandler(any())).thenReturn(pocaEmailHandler);

        byte[] initialArray = {0, 1, 2};
        List<EmailDetail> emailDetails = Arrays.asList(getUserOneEmailDetail(pocaMailId, initialArray), getUserTwoEmailDetail(pocaMailId, initialArray));

        when(pocaEmailHandler.fetchPocaEmailDetails()).thenReturn(emailDetails);

        JsonObject jsonObject = objectToJsonObjectConverter.convert(new MailServerCredentials());
        when(executionInfo.getJobData()).thenReturn(jsonObject);

        when(fileStorer.store(any(), any()))
                .thenThrow(new FileServiceException(MESSAGE))
                .thenThrow(new FileServiceException(MESSAGE))
                .thenThrow(new FileServiceException(MESSAGE))
                .thenReturn(pocaFileId);

        final ExecutionInfo actualExecutionInfo = pocaEmailsTask.execute(executionInfo);

        assertThat(actualExecutionInfo.getExecutionStatus(), is(COMPLETED));

        verify(pocaApplicationCommandSender).processPocaEmail(pocaFileId, pocaMailId, emailDetails.get(1).getSenderEmail(), emailDetails.get(1).getSubject());
        verify(pocaEmailHandler).deleteEmail(emailDetails.get(1));

    }

    @Test
    public void shouldReturnTaskCompletedWhenThereIsNoPocaEmail() throws MessagingException, IOException {

        final ExecutionInfo executionInfo = mock(ExecutionInfo.class);
        MailServerCredentials mailServerCredentials = new MailServerCredentials();
        JsonObject jsonObject = objectToJsonObjectConverter.convert(mailServerCredentials);

        when(pocaEmailHandler.fetchPocaEmailDetails()).thenReturn(null);
        when(emailHandlerFactory.createPocaEmailHandler(any(MailServerCredentials.class))).thenReturn(pocaEmailHandler);
        when(executionInfo.getJobData()).thenReturn(jsonObject);

        final ExecutionInfo actualExecutionInfo = pocaEmailsTask.execute(executionInfo);

        assertThat(actualExecutionInfo.getExecutionStatus(), is(COMPLETED));

    }

    @Test
    public void shouldReturnTaskCompletedAndRaiseCommandToRecordFailureWhenThereIsException() throws MessagingException, IOException {

        final ExecutionInfo executionInfo = mock(ExecutionInfo.class);
        MailServerCredentials mailServerCredentials = new MailServerCredentials();
        mailServerCredentials.setServer(MAIL_SERVER);
        JsonObject jsonObject = objectToJsonObjectConverter.convert(mailServerCredentials);

        when(emailHandlerFactory.createPocaEmailHandler(any(MailServerCredentials.class))).thenReturn(pocaEmailHandler);
        when(executionInfo.getJobData()).thenReturn(jsonObject);
        when(pocaEmailHandler.fetchPocaEmailDetails()).thenThrow(new IOException("could not connect"));

        final ExecutionInfo actualExecutionInfo = pocaEmailsTask.execute(executionInfo);

        assertThat(actualExecutionInfo.getExecutionStatus(), is(COMPLETED));
    }

    private EmailDetail getUserOneEmailDetail(UUID pocaMailId, byte[] initialArray) {
        return emailDetails()
                .withSenderEmail("userone@test.com")
                .withSubject("user one subject")
                .withFileName("iw033-eng-new.docx")
                .withPocaMailId(pocaMailId)
                .withDocumentContent(new ByteArrayInputStream(initialArray))
                .build();
    }
    private EmailDetail getUserTwoEmailDetail(UUID pocaMailId, byte[] initialArray) {
        return emailDetails()
                .withSenderEmail("usertwo@test.com")
                .withSubject("user two subject")
                .withFileName("iw033-eng-new.docx")
                .withPocaMailId(pocaMailId)
                .withDocumentContent(new ByteArrayInputStream(initialArray))
                .build();
    }


    private void assertPocaEmailDetails(UUID pocaFileId, UUID pocaMailId, EmailDetail emailDetail, ExecutionInfo actualExecutionInfo) throws MessagingException {

        verify(pocaApplicationCommandSender).processPocaEmail(pocaFileId, pocaMailId, emailDetail.getSenderEmail(), emailDetail.getSubject());
        verify(pocaEmailHandler).deleteEmail(emailDetail);
        assertThat(actualExecutionInfo.getExecutionStatus(), is(COMPLETED));
    }
}