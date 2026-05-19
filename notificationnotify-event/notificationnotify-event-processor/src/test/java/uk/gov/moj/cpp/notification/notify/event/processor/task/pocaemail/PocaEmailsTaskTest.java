package uk.gov.moj.cpp.notification.notify.event.processor.task.pocaemail;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.COMPLETED;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.domain.EmailDetail.emailDetails;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import com.azure.storage.blob.models.BlobStorageException;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.notification.notify.event.processor.NotificationNotifyCommandSender;
import uk.gov.moj.cpp.notification.notify.event.processor.PocaApplicationCommandSender;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.EmailDetail;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.MailServerCredentials;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.EmailHandlerFactory;
import uk.gov.moj.cpp.notification.notify.event.processor.task.handlers.PocaEmailHandler;
import uk.gov.moj.cpp.notification.notify.filestore.azure.FileStorer;
import uk.gov.moj.cpp.notification.notify.filestore.azure.StoragePath;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
    private static final String EXPECTED_FAILURE_MESSAGE = "Failed to upload document 'iw033-eng-new.docx' to blob storage after 3 attempts";

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
    public void shouldReturnPocaEmailsTaskAsTheNextTaskWhenPocaEmailsExists() throws MessagingException, IOException {

        final UUID pocaMailId = new UUID(new Date().getTime(), 1L);
        final ExecutionInfo executionInfo = mock(ExecutionInfo.class);

        when(emailHandlerFactory.createPocaEmailHandler(any(MailServerCredentials.class))).thenReturn(pocaEmailHandler);

        final byte[] initialArray = {0, 1, 2};
        final List<EmailDetail> emailDetails = Arrays.asList(
                getUserOneEmailDetail(pocaMailId, initialArray),
                getUserTwoEmailDetail(pocaMailId, initialArray));

        when(pocaEmailHandler.fetchPocaEmailDetails()).thenReturn(emailDetails);
        when(executionInfo.getJobData()).thenReturn(objectToJsonObjectConverter.convert(new MailServerCredentials()));

        final UUID fileIdOne = randomUUID();
        final UUID fileIdTwo = randomUUID();
        when(fileStorer.store(isA(StoragePath.class), isA(UUID.class), eq("iw033-eng-new.docx"), isA(InputStream.class)))
                .thenReturn(fileIdOne, fileIdTwo);

        final ExecutionInfo actualExecutionInfo = pocaEmailsTask.execute(executionInfo);

        verify(pocaApplicationCommandSender).processPocaEmail(fileIdOne, pocaMailId, "userone@test.com", "user one subject");
        verify(pocaApplicationCommandSender).processPocaEmail(fileIdTwo, pocaMailId, "usertwo@test.com", "user two subject");
        verify(pocaEmailHandler).deleteEmail(emailDetails.get(0));
        verify(pocaEmailHandler).deleteEmail(emailDetails.get(1));
        assertThat(actualExecutionInfo.getExecutionStatus(), is(COMPLETED));
    }

    @Test
    public void shouldRaiseCommandToRecordFailureWhenUploadExhaustsRetries() throws MessagingException, IOException {

        final UUID pocaMailId = new UUID(new Date().getTime(), 1L);
        final ExecutionInfo executionInfo = mock(ExecutionInfo.class);

        when(emailHandlerFactory.createPocaEmailHandler(any(MailServerCredentials.class))).thenReturn(pocaEmailHandler);

        final byte[] initialArray = {0, 1, 2};
        when(pocaEmailHandler.fetchPocaEmailDetails()).thenReturn(List.of(getUserOneEmailDetail(pocaMailId, initialArray)));

        final MailServerCredentials mailServerCredentials = new MailServerCredentials();
        mailServerCredentials.setServer(MAIL_SERVER);
        when(executionInfo.getJobData()).thenReturn(objectToJsonObjectConverter.convert(mailServerCredentials));
        when(fileStorer.store(isA(StoragePath.class), isA(UUID.class), eq("iw033-eng-new.docx"), isA(InputStream.class)))
                .thenThrow(mock(BlobStorageException.class))
                .thenThrow(mock(BlobStorageException.class))
                .thenThrow(mock(BlobStorageException.class));

        final ExecutionInfo actualExecutionInfo = pocaEmailsTask.execute(executionInfo);

        assertThat(actualExecutionInfo.getExecutionStatus(), is(COMPLETED));
        verify(notificationNotifyCommandSender).recordCheckPocaEmailRequestAsFailed(MAIL_SERVER, EXPECTED_FAILURE_MESSAGE);
    }

    @Test
    public void shouldProcessSecondEmailWhenFirstEmailUploadExhaustsRetries() throws MessagingException, IOException {

        final UUID pocaMailId = new UUID(new Date().getTime(), 1L);
        final ExecutionInfo executionInfo = mock(ExecutionInfo.class);

        when(emailHandlerFactory.createPocaEmailHandler(any(MailServerCredentials.class))).thenReturn(pocaEmailHandler);

        final byte[] initialArray = {0, 1, 2};
        final List<EmailDetail> emailDetails = Arrays.asList(
                getUserOneEmailDetail(pocaMailId, initialArray),
                getUserTwoEmailDetail(pocaMailId, initialArray));

        when(pocaEmailHandler.fetchPocaEmailDetails()).thenReturn(emailDetails);

        final MailServerCredentials mailServerCredentials = new MailServerCredentials();
        mailServerCredentials.setServer(MAIL_SERVER);
        when(executionInfo.getJobData()).thenReturn(objectToJsonObjectConverter.convert(mailServerCredentials));

        final UUID expectedFileIdTwo = randomUUID();
        when(fileStorer.store(isA(StoragePath.class), isA(UUID.class), eq("iw033-eng-new.docx"), isA(InputStream.class)))
                .thenThrow(mock(BlobStorageException.class))
                .thenThrow(mock(BlobStorageException.class))
                .thenThrow(mock(BlobStorageException.class))
                .thenReturn(expectedFileIdTwo);

        final ExecutionInfo actualExecutionInfo = pocaEmailsTask.execute(executionInfo);

        assertThat(actualExecutionInfo.getExecutionStatus(), is(COMPLETED));

        verify(pocaApplicationCommandSender).processPocaEmail(
                eq(expectedFileIdTwo), eq(pocaMailId), eq("usertwo@test.com"), eq("user two subject"));
        verify(pocaEmailHandler).deleteEmail(emailDetails.get(1));
    }

    @Test
    public void shouldReturnTaskCompletedWhenThereIsNoPocaEmail() throws MessagingException, IOException {

        final ExecutionInfo executionInfo = mock(ExecutionInfo.class);
        final JsonObject jsonObject = objectToJsonObjectConverter.convert(new MailServerCredentials());

        when(pocaEmailHandler.fetchPocaEmailDetails()).thenReturn(null);
        when(emailHandlerFactory.createPocaEmailHandler(any(MailServerCredentials.class))).thenReturn(pocaEmailHandler);
        when(executionInfo.getJobData()).thenReturn(jsonObject);

        final ExecutionInfo actualExecutionInfo = pocaEmailsTask.execute(executionInfo);

        assertThat(actualExecutionInfo.getExecutionStatus(), is(COMPLETED));
    }

    @Test
    public void shouldReturnTaskCompletedWhenThereIsException() throws MessagingException, IOException {

        final ExecutionInfo executionInfo = mock(ExecutionInfo.class);
        final MailServerCredentials mailServerCredentials = new MailServerCredentials();
        mailServerCredentials.setServer(MAIL_SERVER);
        final JsonObject jsonObject = objectToJsonObjectConverter.convert(mailServerCredentials);

        when(emailHandlerFactory.createPocaEmailHandler(any(MailServerCredentials.class))).thenReturn(pocaEmailHandler);
        when(executionInfo.getJobData()).thenReturn(jsonObject);
        when(pocaEmailHandler.fetchPocaEmailDetails()).thenThrow(new IOException("could not connect"));

        final ExecutionInfo actualExecutionInfo = pocaEmailsTask.execute(executionInfo);

        assertThat(actualExecutionInfo.getExecutionStatus(), is(COMPLETED));
    }

    @Test
    public void shouldRecordFailureWhenDocumentContentThrowsIOException() throws MessagingException, IOException {

        final UUID pocaMailId = new UUID(new Date().getTime(), 1L);
        final ExecutionInfo executionInfo = mock(ExecutionInfo.class);
        final MailServerCredentials mailServerCredentials = new MailServerCredentials();
        mailServerCredentials.setServer(MAIL_SERVER);

        final InputStream brokenStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("disk read error");
            }
        };

        final EmailDetail emailDetail = emailDetails()
                .withSenderEmail("userone@test.com")
                .withSubject("user one subject")
                .withFileName("iw033-eng-new.docx")
                .withPocaMailId(pocaMailId)
                .withDocumentContent(brokenStream)
                .build();

        when(emailHandlerFactory.createPocaEmailHandler(any(MailServerCredentials.class))).thenReturn(pocaEmailHandler);
        when(pocaEmailHandler.fetchPocaEmailDetails()).thenReturn(List.of(emailDetail));
        when(executionInfo.getJobData()).thenReturn(objectToJsonObjectConverter.convert(mailServerCredentials));

        final ExecutionInfo actualExecutionInfo = pocaEmailsTask.execute(executionInfo);

        assertThat(actualExecutionInfo.getExecutionStatus(), is(COMPLETED));
        verify(notificationNotifyCommandSender).recordCheckPocaEmailRequestAsFailed(
                MAIL_SERVER, "Unable to read document content from email attachment");
    }

    private EmailDetail getUserOneEmailDetail(final UUID pocaMailId, final byte[] initialArray) {
        return emailDetails()
                .withSenderEmail("userone@test.com")
                .withSubject("user one subject")
                .withFileName("iw033-eng-new.docx")
                .withPocaMailId(pocaMailId)
                .withDocumentContent(new ByteArrayInputStream(initialArray))
                .build();
    }

    private EmailDetail getUserTwoEmailDetail(final UUID pocaMailId, final byte[] initialArray) {
        return emailDetails()
                .withSenderEmail("usertwo@test.com")
                .withSubject("user two subject")
                .withFileName("iw033-eng-new.docx")
                .withPocaMailId(pocaMailId)
                .withDocumentContent(new ByteArrayInputStream(initialArray))
                .build();
    }
}
