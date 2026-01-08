package uk.gov.moj.cpp.notification.notify.event.processor.sender;

import static com.google.common.io.Resources.getResource;
import static java.lang.String.format;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.UUID.fromString;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static org.apache.commons.lang3.StringUtils.chomp;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;

import org.json.JSONObject;

import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.moj.cpp.notification.notify.event.processor.client.GovNotifyClientProvider;
import uk.gov.moj.cpp.notification.notify.event.processor.client.Office365EmailResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.download.SuccessfulDocumentDownload;
import uk.gov.moj.cpp.notification.notify.event.processor.response.DownloadResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.Office365SenderResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.SenderResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.SendEmailDetails;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.SendEmailDetailsJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.util.PersonalisationExtractor;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jose4j.base64url.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;


@ExtendWith(MockitoExtension.class)
public class EmailSenderTest {
    private static final int NON_HTTP_ERROR_CONDITION = 999;

    @Mock
    private GovNotifyClientProvider govNotifyClientProvider;

    @Mock
    private PersonalisationExtractor personalisationExtractor;

    @Mock
    private EmailMaterialDownloader emailMaterialDownloader;

    @Mock
    private AttachmentsRetriever attachmentsRetriever;

    @Mock
    private Logger logger;

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private MicrosoftOffice365ClientService microsoftOffice365ClientService;

    @Mock
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;

    @InjectMocks
    private EmailSender emailSender;


    @Test
    public void shouldSuccessfullySendEmailThroughGovNotifyWithMaterialUrlAttachmentAndDomainNotListed() throws Exception {

        final UUID templateId = fromString("e15e255f-c84b-4557-a787-138f27a3f554");
        final UUID notificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final UUID externalNotificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final String sendToAddress = "sendToAddress";
        final UUID replyToAddressId = fromString("9f476d4f-4069-444a-a511-905dcbb66910");
        final Map<String, Object> personalisationMap = new HashMap<>();

        final SendEmailDetails sendEmailDetails = mock(SendEmailDetails.class);
        final SendEmailResponse sendEmailResponse = mock(SendEmailResponse.class);
        final SendEmailDetailsJobState notificationJobState = mock(SendEmailDetailsJobState.class);

        when(notificationJobState.getNotificationId()).thenReturn(notificationId);
        when(notificationJobState.getTaskPayload()).thenReturn(sendEmailDetails);
        when(sendEmailDetails.getTemplateId()).thenReturn(templateId);
        when(sendEmailDetails.getSendToAddress()).thenReturn(sendToAddress);
        when(sendEmailDetails.getReplyToAddressId()).thenReturn(of(replyToAddressId));
        when(govNotifyClientProvider.getClient()).thenReturn(notificationClient);
        when(personalisationExtractor.extractFrom(sendEmailDetails)).thenReturn(personalisationMap);
        final String materialUrl = "document url";
        final int contentSizeInBytes = 1834;
        final byte[] documentContent = "content".getBytes();

        final InputStream emailInputStream = new ByteArrayInputStream(documentContent);
        final SuccessfulDocumentDownload successfulDocumentDownload = new SuccessfulDocumentDownload(200, emailInputStream, contentSizeInBytes, documentContent, "sampleFile.pdf");
        when(sendEmailDetails.getMaterialUrl()).thenReturn(of(materialUrl));
        final DownloadResponse downloadResponse = new DownloadResponse(successfulDocumentDownload);
        when(emailMaterialDownloader.downloadMaterial(notificationId, sendEmailDetails)).thenReturn(downloadResponse);

        when(notificationClient.sendEmail(templateId.toString(),
                sendEmailDetails.getSendToAddress(), personalisationExtractor.extractFrom(sendEmailDetails),
                notificationId.toString(), replyToAddressId.toString())).thenReturn(sendEmailResponse);

        when(sendEmailResponse.getNotificationId()).thenReturn(externalNotificationId);
        when(logger.isInfoEnabled()).thenReturn(true);

        final SenderResponse senderResponse = (SenderResponse) emailSender.send(notificationJobState);

        assertThat(senderResponse.isSuccessful(), is(true));
        assertThat(senderResponse.getExternalNotificationId(), is(externalNotificationId));

        verify(govNotifyClientProvider).getClient();
        verifyNoInteractions(microsoftOffice365ClientService);

        verify(logger).info("Sending email with template Id 'e15e255f-c84b-4557-a787-138f27a3f554' and notification Id '0bc5cacf-245b-488c-bc95-2ab182977d2d'");
    }

    @Test
    public void shouldSuccessfullySendEmailThroughGovNotifyWithFileIdAttachmentAndDomainNotListed() throws Exception {

        final UUID templateId = fromString("e15e255f-c84b-4557-a787-138f27a3f554");
        final UUID notificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final UUID externalNotificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final String sendToAddress = "sendToAddress";
        final UUID replyToAddressId = fromString("9f476d4f-4069-444a-a511-905dcbb66910");
        final Map<String, Object> personalisationMap = new HashMap<>();

        final SendEmailDetails sendEmailDetails = mock(SendEmailDetails.class);
        final SendEmailResponse sendEmailResponse = mock(SendEmailResponse.class);
        final SendEmailDetailsJobState notificationJobState = mock(SendEmailDetailsJobState.class);

        when(notificationJobState.getNotificationId()).thenReturn(notificationId);
        when(notificationJobState.getTaskPayload()).thenReturn(sendEmailDetails);
        when(sendEmailDetails.getTemplateId()).thenReturn(templateId);
        when(sendEmailDetails.getSendToAddress()).thenReturn(sendToAddress);
        when(sendEmailDetails.getReplyToAddressId()).thenReturn(of(replyToAddressId));
        when(govNotifyClientProvider.getClient()).thenReturn(notificationClient);
        when(personalisationExtractor.extractFrom(sendEmailDetails)).thenReturn(personalisationMap);
        final UUID fileId = fromString("abcdcacf-245b-488c-bc95-2ab182977d2d");
        final int contentSizeInBytes = 1834;
        final byte[] documentContent = "content".getBytes();

        final InputStream emailInputStream = new ByteArrayInputStream(documentContent);
        final SuccessfulDocumentDownload successfulDocumentDownload = new SuccessfulDocumentDownload(200, emailInputStream, contentSizeInBytes, documentContent, "sampleFile.pdf");
        when(sendEmailDetails.getMaterialUrl()).thenReturn(empty());
        when(sendEmailDetails.getFileId()).thenReturn(of(fileId));
        final DownloadResponse downloadResponse = new DownloadResponse(successfulDocumentDownload);
        when(attachmentsRetriever.getAttachment(notificationId, fileId)).thenReturn(downloadResponse);

        when(notificationClient.sendEmail(templateId.toString(),
                sendEmailDetails.getSendToAddress(), personalisationExtractor.extractFrom(sendEmailDetails),
                notificationId.toString(), replyToAddressId.toString())).thenReturn(sendEmailResponse);

        when(sendEmailResponse.getNotificationId()).thenReturn(externalNotificationId);
        when(logger.isInfoEnabled()).thenReturn(true);

        final SenderResponse senderResponse = (SenderResponse) emailSender.send(notificationJobState);

        assertThat(senderResponse.isSuccessful(), is(true));
        assertThat(senderResponse.getExternalNotificationId(), is(externalNotificationId));

        verify(govNotifyClientProvider).getClient();
        verifyNoInteractions(microsoftOffice365ClientService);

        verify(logger).info("Sending email with template Id 'e15e255f-c84b-4557-a787-138f27a3f554' and notification Id '0bc5cacf-245b-488c-bc95-2ab182977d2d'");
    }

    @Test
    public void shouldReturnErrorResponseIfNotificationClientExceptionIsThrown() throws Exception {

        final String materialUrl = "document url";
        final int contentSizeInBytes = 1834;

        final UUID templateId = fromString("e15e255f-c84b-4557-a787-138f27a3f554");
        final UUID notificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final String sendToAddress = "sendToAddress";
        final Map<String, Object> personalisationMap = new HashMap<>();

        final NotificationClientException notificationClientException = mock(NotificationClientException.class);

        final int statusCode = SC_BAD_REQUEST;
        final String exceptionMessage = "Goodness gracious me";

        final SendEmailDetails sendEmailDetails = mock(SendEmailDetails.class);
        final SendEmailDetailsJobState notificationJobState = mock(SendEmailDetailsJobState.class);
        when(notificationJobState.getNotificationId()).thenReturn(notificationId);
        when(notificationJobState.getTaskPayload()).thenReturn(sendEmailDetails);
        when(sendEmailDetails.getTemplateId()).thenReturn(templateId);
        when(sendEmailDetails.getSendToAddress()).thenReturn(sendToAddress);
        when(sendEmailDetails.getReplyToAddressId()).thenReturn(empty());
        final byte[] documentContent = "content".getBytes();
        final InputStream emailInputStream = new ByteArrayInputStream(documentContent);

        final SuccessfulDocumentDownload successfulDocumentDownload = new SuccessfulDocumentDownload(200, emailInputStream, contentSizeInBytes, documentContent, "sampleFile.pdf");
        when(sendEmailDetails.getMaterialUrl()).thenReturn(of(materialUrl));
        final DownloadResponse downloadResponse = new DownloadResponse(successfulDocumentDownload);
        when(emailMaterialDownloader.downloadMaterial(notificationId, sendEmailDetails)).thenReturn(downloadResponse);

        when(govNotifyClientProvider.getClient()).thenReturn(notificationClient);
        when(personalisationExtractor.extractFrom(sendEmailDetails)).thenReturn(personalisationMap);
        when(notificationClient.sendEmail(templateId.toString(), sendToAddress, personalisationMap, notificationId.toString(),
                "")).thenThrow(notificationClientException);
        when(notificationClientException.getHttpResult()).thenReturn(statusCode);
        when(notificationClientException.getLocalizedMessage()).thenReturn(exceptionMessage);
        when(sendEmailDetails.getMaterialUrl()).thenReturn(of(materialUrl));
        when(logger.isInfoEnabled()).thenReturn(true);
        when(logger.isErrorEnabled()).thenReturn(true);

        final ErrorResponse errorResponse = (ErrorResponse) emailSender.send(notificationJobState);

        assertThat(errorResponse.getStatusCode(), is(statusCode));
        assertThat(errorResponse.getErrorMessage(), is("Gov.Notify responded with 'Goodness gracious me'"));

        verify(govNotifyClientProvider).getClient();
        verifyNoInteractions(microsoftOffice365ClientService);

        final InOrder inOrder = inOrder(logger);
        inOrder.verify(logger).info("Sending email with template Id 'e15e255f-c84b-4557-a787-138f27a3f554' and notification Id '0bc5cacf-245b-488c-bc95-2ab182977d2d'");
        inOrder.verify(logger).error("An error was thrown while sending email", notificationClientException);
    }

    @Test
    public void shouldReturnErrorResponseIfRuntimeExceptionIsThrown() throws Exception {

        final UUID templateId = fromString("e15e255f-c84b-4557-a787-138f27a3f554");
        final UUID notificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final String sendToAddress = "sendToAddress";
        final Map<String, Object> personalisationMap = new HashMap<>();

        final RuntimeException runtimeException = mock(RuntimeException.class);
        final String exceptionMessage = "Goodness gracious me";

        final SendEmailDetails sendEmailDetails = mock(SendEmailDetails.class);
        final SendEmailDetailsJobState notificationJobState = mock(SendEmailDetailsJobState.class);

        final String materialUrl = "document url";
        final int contentSizeInBytes = 1834;
        when(sendEmailDetails.getReplyToAddressId()).thenReturn(empty());
        final byte[] documentContent = "content".getBytes();
        final InputStream emailInputStream = new ByteArrayInputStream(documentContent);
        final SuccessfulDocumentDownload successfulDocumentDownload = new SuccessfulDocumentDownload(200, emailInputStream, contentSizeInBytes, documentContent, "sampleFile.pdf");
        when(sendEmailDetails.getMaterialUrl()).thenReturn(of(materialUrl));
        final DownloadResponse downloadResponse = new DownloadResponse(successfulDocumentDownload);
        when(emailMaterialDownloader.downloadMaterial(notificationId, sendEmailDetails)).thenReturn(downloadResponse);


        when(notificationJobState.getNotificationId()).thenReturn(notificationId);
        when(notificationJobState.getTaskPayload()).thenReturn(sendEmailDetails);
        when(sendEmailDetails.getTemplateId()).thenReturn(templateId);
        when(sendEmailDetails.getSendToAddress()).thenReturn(sendToAddress);
        when(sendEmailDetails.getReplyToAddressId()).thenReturn(empty());
        when(govNotifyClientProvider.getClient()).thenReturn(notificationClient);
        when(personalisationExtractor.extractFrom(sendEmailDetails)).thenReturn(personalisationMap);
        when(notificationClient.sendEmail(templateId.toString(), sendToAddress, personalisationMap, notificationId.toString(),
                "")).thenThrow(runtimeException);
        when(runtimeException.getLocalizedMessage()).thenReturn(exceptionMessage);
        when(logger.isInfoEnabled()).thenReturn(true);
        when(logger.isErrorEnabled()).thenReturn(true);

        final ErrorResponse errorResponse = (ErrorResponse) emailSender.send(notificationJobState);

        assertThat(errorResponse.isSuccessful(), is(false));
        assertThat(errorResponse.getStatusCode(), is(NON_HTTP_ERROR_CONDITION));
        assertThat(errorResponse.getErrorMessage(),
                is(format("Permanent failure, unexpected error while trying to deliver notification Id '%s', error message: '%s'", notificationId, exceptionMessage)));

        verify(govNotifyClientProvider).getClient();
        verifyNoInteractions(microsoftOffice365ClientService);

        final InOrder inOrder = inOrder(logger);

        inOrder.verify(logger).info("Sending email with template Id 'e15e255f-c84b-4557-a787-138f27a3f554' and notification Id '0bc5cacf-245b-488c-bc95-2ab182977d2d'");
        inOrder.verify(logger).error("An unexpected error was thrown while sending email", runtimeException);
    }

    @Test
    public void shouldReturnAnErrorIfTheEmailFileLinkDownloadedExceedsMaxLimitOf15MB() throws Exception {
        final int httpResult = 413;
        final int contentSizeInBytes = 15728641;
        final String message = chomp(format("'Size too Big '"));
        final String expectedMessage = "Status code: 413 Document is larger than 15MB  with content size 15728641";

        final NotificationClientException notificationClientException = new NotificationClientException(message);
        setField(notificationClientException, "httpResult", httpResult);
        final UUID notificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final String materialUrl = "document url";
        final SendEmailDetails sendEmailDetails = mock(SendEmailDetails.class);
        final SendEmailDetailsJobState notificationJobState = mock(SendEmailDetailsJobState.class);

        final byte[] documentContentInByteArray = getFileContent("pdf/LargeSample.pdf");
        final InputStream emailInputStream = new ByteArrayInputStream(documentContentInByteArray);
        final SuccessfulDocumentDownload successfulDocumentDownload = new SuccessfulDocumentDownload(httpResult, emailInputStream, contentSizeInBytes, documentContentInByteArray, "LargeSample.pdf");

        when(notificationJobState.getNotificationId()).thenReturn(notificationId);
        when(notificationJobState.getTaskPayload()).thenReturn(sendEmailDetails);
        when(sendEmailDetails.getMaterialUrl()).thenReturn(of(materialUrl));
        when(sendEmailDetails.getMaterialUrl()).thenReturn(of(materialUrl));
        when(sendEmailDetails.getMaterialUrl()).thenReturn(of(materialUrl));
        when(logger.isErrorEnabled()).thenReturn(true);
        final DownloadResponse downloadResponse = new DownloadResponse(successfulDocumentDownload);
        when(emailMaterialDownloader.downloadMaterial(notificationId, sendEmailDetails)).thenReturn(downloadResponse);
        final ErrorResponse errorResponse = (ErrorResponse) emailSender.send(notificationJobState);

        assertThat(errorResponse.getStatusCode(), is(httpResult));
        assertThat(errorResponse.getErrorMessage(), is(expectedMessage));

        verifyNoInteractions(govNotifyClientProvider);
        verifyNoInteractions(microsoftOffice365ClientService);

        final InOrder inOrder = inOrder(logger);
        inOrder.verify(logger).error("An error was thrown while sending email for Notification: {} file size {}", notificationId, contentSizeInBytes);

    }

    @Test
    public void shouldReturnSuccessfulResponseThroughMicrosoftOffice365WhenAttachmentSizeBetween2To15MB() throws Exception {

        final UUID templateId = fromString("e15e255f-c84b-4557-a787-138f27a3f554");
        final UUID notificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final UUID externalNotificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final String sendToAddress = "sendToAddress";
        final UUID replyToAddressId = fromString("9f476d4f-4069-444a-a511-905dcbb66910");
        final Map<String, Object> personalisationMap = new HashMap<>();

        final SendEmailDetails sendEmailDetails = mock(SendEmailDetails.class);
        final Office365EmailResponse office365EmailResponse = mock(Office365EmailResponse.class);
        final SendEmailDetailsJobState notificationJobState = mock(SendEmailDetailsJobState.class);

        when(notificationJobState.getNotificationId()).thenReturn(notificationId);
        when(notificationJobState.getTaskPayload()).thenReturn(sendEmailDetails);
        when(sendEmailDetails.getTemplateId()).thenReturn(templateId);
        when(sendEmailDetails.getSendToAddress()).thenReturn(sendToAddress);
        when(sendEmailDetails.getReplyToAddressId()).thenReturn(of(replyToAddressId));
        when(personalisationExtractor.extractFrom(sendEmailDetails)).thenReturn(personalisationMap);
        final String materialUrl = "document url";
        final int contentSizeInBytes = 2097153;
        final byte[] documentContent = "content".getBytes();
        final String encodedDocumentContent = Base64.encode(documentContent);

        final InputStream emailInputStream = new ByteArrayInputStream(documentContent);
        final SuccessfulDocumentDownload successfulDocumentDownload = new SuccessfulDocumentDownload(200, emailInputStream, contentSizeInBytes, documentContent, "SampleFile");
        when(sendEmailDetails.getMaterialUrl()).thenReturn(of(materialUrl));
        final DownloadResponse downloadResponse = new DownloadResponse(successfulDocumentDownload);
        when(emailMaterialDownloader.downloadMaterial(notificationId, sendEmailDetails)).thenReturn(downloadResponse);

        when(microsoftOffice365ClientService.sendEmailByMicrosoftOffice365(templateId.toString(),
                sendEmailDetails.getSendToAddress(),
                notificationId.toString(), replyToAddressId.toString(), personalisationMap, "SampleFile", encodedDocumentContent)).thenReturn(office365EmailResponse);

        when(office365EmailResponse.getNotificationId()).thenReturn(externalNotificationId);
        when(logger.isInfoEnabled()).thenReturn(true);
        final JSONObject data = getJsonObjectData();
        when(office365EmailResponse.getData()).thenReturn(data);

        final Office365SenderResponse office365SenderResponse = (Office365SenderResponse) emailSender.send(notificationJobState);

        assertThat(office365SenderResponse.isSuccessful(), is(true));
        assertThat(office365SenderResponse.getExternalNotificationId(), is(externalNotificationId));

        verify(microsoftOffice365ClientService).sendEmailByMicrosoftOffice365(
                anyString(), anyString(), anyString(), anyString(), anyMap(), anyString(), anyString());
        verifyNoInteractions(govNotifyClientProvider);

        verify(logger).info("Sending email with template Id 'e15e255f-c84b-4557-a787-138f27a3f554' and notification Id '0bc5cacf-245b-488c-bc95-2ab182977d2d'");
    }

    @Test
    public void shouldReturnSuccessfulResponseThroughMicrosoftOffice365WhenAttachmentSizeBetween2To15MBAndSenderIsListedDomain() throws Exception {

        final UUID templateId = fromString("e15e255f-c84b-4557-a787-138f27a3f554");
        final UUID notificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final UUID externalNotificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final String sendToAddress = "sendToAddress";
        final UUID replyToAddressId = fromString("9f476d4f-4069-444a-a511-905dcbb66910");
        final Map<String, Object> personalisationMap = new HashMap<>();

        final SendEmailDetails sendEmailDetails = mock(SendEmailDetails.class);
        final Office365EmailResponse office365EmailResponse = mock(Office365EmailResponse.class);
        final SendEmailDetailsJobState notificationJobState = mock(SendEmailDetailsJobState.class);

        when(notificationJobState.getNotificationId()).thenReturn(notificationId);
        when(notificationJobState.getTaskPayload()).thenReturn(sendEmailDetails);
        when(sendEmailDetails.getTemplateId()).thenReturn(templateId);
        when(sendEmailDetails.getSendToAddress()).thenReturn(sendToAddress);
        when(sendEmailDetails.getReplyToAddressId()).thenReturn(of(replyToAddressId));
        when(personalisationExtractor.extractFrom(sendEmailDetails)).thenReturn(personalisationMap);
        final String materialUrl = "document url";
        final int contentSizeInBytes = 2097153;
        final byte[] documentContent = "content".getBytes();
        final String encodedDocumentContent = Base64.encode(documentContent);

        final InputStream emailInputStream = new ByteArrayInputStream(documentContent);
        final SuccessfulDocumentDownload successfulDocumentDownload = new SuccessfulDocumentDownload(200, emailInputStream, contentSizeInBytes, documentContent, "SampleFile");
        when(sendEmailDetails.getMaterialUrl()).thenReturn(of(materialUrl));
        final DownloadResponse downloadResponse = new DownloadResponse(successfulDocumentDownload);
        when(emailMaterialDownloader.downloadMaterial(notificationId, sendEmailDetails)).thenReturn(downloadResponse);

        final JSONObject data = getJsonObjectData();
        when(office365EmailResponse.getData()).thenReturn(data);
        when(microsoftOffice365ClientService.sendEmailByMicrosoftOffice365(templateId.toString(),
                sendEmailDetails.getSendToAddress(),
                notificationId.toString(), replyToAddressId.toString(), personalisationMap, "SampleFile", encodedDocumentContent)).thenReturn(office365EmailResponse);

        when(office365EmailResponse.getNotificationId()).thenReturn(externalNotificationId);
        when(logger.isInfoEnabled()).thenReturn(true);

        final Office365SenderResponse office365SenderResponse = (Office365SenderResponse) emailSender.send(notificationJobState);

        assertThat(office365SenderResponse.isSuccessful(), is(true));
        assertThat(office365SenderResponse.getExternalNotificationId(), is(externalNotificationId));

        verify(microsoftOffice365ClientService).sendEmailByMicrosoftOffice365(
                anyString(), anyString(), anyString(), anyString(), anyMap(), anyString(), anyString());
        verifyNoInteractions(govNotifyClientProvider);

        verify(logger).info("Sending email with template Id 'e15e255f-c84b-4557-a787-138f27a3f554' and notification Id '0bc5cacf-245b-488c-bc95-2ab182977d2d'");
    }

    @Test
    public void shouldNotReturnAnErrorIfTheEmailFileLinkIsEmptyOrNotPresent() throws Exception {

        final UUID templateId = fromString("e15e255f-c84b-4557-a787-138f27a3f554");
        final UUID notificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final UUID externalNotificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final String sendToAddress = "sendToAddress";
        final UUID replyToAddressId = fromString("9f476d4f-4069-444a-a511-905dcbb66910");
        final Map<String, Object> personalisationMap = new HashMap<>();

        final SendEmailDetails sendEmailDetails = mock(SendEmailDetails.class);
        final SendEmailResponse sendEmailResponse = mock(SendEmailResponse.class);
        final SendEmailDetailsJobState notificationJobState = mock(SendEmailDetailsJobState.class);

        when(notificationJobState.getNotificationId()).thenReturn(notificationId);
        when(notificationJobState.getTaskPayload()).thenReturn(sendEmailDetails);
        when(sendEmailDetails.getTemplateId()).thenReturn(templateId);
        when(sendEmailDetails.getSendToAddress()).thenReturn(sendToAddress);
        when(sendEmailDetails.getReplyToAddressId()).thenReturn(of(replyToAddressId));
        when(govNotifyClientProvider.getClient()).thenReturn(notificationClient);
        when(personalisationExtractor.extractFrom(sendEmailDetails)).thenReturn(personalisationMap);
        final String materialUrl = "";
        final int contentSizeInBytes = 1834;

        final byte[] documentContent = "content".getBytes();
        final InputStream emailInputStream = new ByteArrayInputStream(documentContent);
        final SuccessfulDocumentDownload successfulDocumentDownload = new SuccessfulDocumentDownload(200, emailInputStream, contentSizeInBytes, documentContent, "sampleFILE.PDF");
        when(sendEmailDetails.getMaterialUrl()).thenReturn(of(materialUrl));
        when(sendEmailDetails.getFileId()).thenReturn(empty());
        final DownloadResponse downloadResponse = new DownloadResponse(successfulDocumentDownload);

        when(notificationClient.sendEmail(templateId.toString(),
                sendEmailDetails.getSendToAddress(), personalisationExtractor.extractFrom(sendEmailDetails),
                notificationId.toString(), replyToAddressId.toString())).thenReturn(sendEmailResponse);

        when(sendEmailResponse.getNotificationId()).thenReturn(externalNotificationId);
        when(logger.isInfoEnabled()).thenReturn(true);

        final SenderResponse senderResponse = (SenderResponse) emailSender.send(notificationJobState);

        assertThat(senderResponse.isSuccessful(), is(true));
        assertThat(senderResponse.getExternalNotificationId(), is(externalNotificationId));

        verify(govNotifyClientProvider).getClient();
        verifyNoInteractions(microsoftOffice365ClientService);

        verify(logger).info("Sending email with template Id 'e15e255f-c84b-4557-a787-138f27a3f554' and notification Id '0bc5cacf-245b-488c-bc95-2ab182977d2d'");

    }

    @Test
    public void shouldReturnSuccessfulResponseThroughMicrosoftOffice365WhenSenderIsInListedDomainsWithSmallAttachment() throws Exception {

        final UUID templateId = fromString("e15e255f-c84b-4557-a787-138f27a3f554");
        final UUID notificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final UUID externalNotificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final String sendToAddress = "a.tester@cjsm.net";
        final UUID replyToAddressId = fromString("9f476d4f-4069-444a-a511-905dcbb66910");
        final Map<String, Object> personalisationMap = new HashMap<>();

        final SendEmailDetails sendEmailDetails = mock(SendEmailDetails.class);
        final Office365EmailResponse office365EmailResponse = mock(Office365EmailResponse.class);
        final SendEmailDetailsJobState notificationJobState = mock(SendEmailDetailsJobState.class);

        when(notificationJobState.getNotificationId()).thenReturn(notificationId);
        when(notificationJobState.getTaskPayload()).thenReturn(sendEmailDetails);
        when(sendEmailDetails.getTemplateId()).thenReturn(templateId);
        when(sendEmailDetails.getSendToAddress()).thenReturn(sendToAddress);
        when(sendEmailDetails.getReplyToAddressId()).thenReturn(of(replyToAddressId));
        final JSONObject data = getJsonObjectData();
        when(office365EmailResponse.getData()).thenReturn(data);
        when(personalisationExtractor.extractFrom(sendEmailDetails)).thenReturn(personalisationMap);

        final String materialUrl = "document url";
        final int contentSizeInBytes = 123;
        final byte[] documentContent = "content".getBytes();
        final String encodedDocumentContent = Base64.encode(documentContent);

        final InputStream emailInputStream = new ByteArrayInputStream(documentContent);
        final SuccessfulDocumentDownload successfulDocumentDownload = new SuccessfulDocumentDownload(200, emailInputStream, contentSizeInBytes, documentContent, "SampleFile");
        when(sendEmailDetails.getMaterialUrl()).thenReturn(of(materialUrl));
        final DownloadResponse downloadResponse = new DownloadResponse(successfulDocumentDownload);
        when(emailMaterialDownloader.downloadMaterial(notificationId, sendEmailDetails)).thenReturn(downloadResponse);

        when(microsoftOffice365ClientService.sendEmailByMicrosoftOffice365(templateId.toString(),
                sendEmailDetails.getSendToAddress(),
                notificationId.toString(), replyToAddressId.toString(), personalisationMap, "SampleFile", encodedDocumentContent)).thenReturn(office365EmailResponse);

        when(office365EmailResponse.getNotificationId()).thenReturn(externalNotificationId);
        when(logger.isInfoEnabled()).thenReturn(true);

        final Office365SenderResponse office365SenderResponse = (Office365SenderResponse) emailSender.send(notificationJobState);

        assertThat(office365SenderResponse.isSuccessful(), is(true));
        assertThat(office365SenderResponse.getExternalNotificationId(), is(externalNotificationId));

        verify(microsoftOffice365ClientService).sendEmailByMicrosoftOffice365(
                anyString(), anyString(), anyString(), anyString(), anyMap(), anyString(), anyString());
        verifyNoInteractions(govNotifyClientProvider);

        verify(logger).info("Sending email with template Id 'e15e255f-c84b-4557-a787-138f27a3f554' and notification Id '0bc5cacf-245b-488c-bc95-2ab182977d2d'");
    }

    @Test
    public void shouldReturnSuccessfulResponseThroughMicrosoftOffice365WhenSenderIsInListedDomainsWithLargeAttachment() throws Exception {

        final UUID templateId = fromString("e15e255f-c84b-4557-a787-138f27a3f554");
        final UUID notificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final UUID externalNotificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final String sendToAddress = "a.tester@cjsm.net";
        final UUID replyToAddressId = fromString("9f476d4f-4069-444a-a511-905dcbb66910");
        final Map<String, Object> personalisationMap = new HashMap<>();

        final SendEmailDetails sendEmailDetails = mock(SendEmailDetails.class);
        final Office365EmailResponse office365EmailResponse = mock(Office365EmailResponse.class);
        final SendEmailDetailsJobState notificationJobState = mock(SendEmailDetailsJobState.class);

        when(notificationJobState.getNotificationId()).thenReturn(notificationId);
        when(notificationJobState.getTaskPayload()).thenReturn(sendEmailDetails);
        when(sendEmailDetails.getTemplateId()).thenReturn(templateId);
        when(sendEmailDetails.getSendToAddress()).thenReturn(sendToAddress);
        when(sendEmailDetails.getReplyToAddressId()).thenReturn(of(replyToAddressId));
        when(personalisationExtractor.extractFrom(sendEmailDetails)).thenReturn(personalisationMap);

        final String materialUrl = "document url";
        final int contentSizeInBytes = 2097153;
        final byte[] documentContent = "content".getBytes();
        final String encodedDocumentContent = Base64.encode(documentContent);

        final InputStream emailInputStream = new ByteArrayInputStream(documentContent);
        final SuccessfulDocumentDownload successfulDocumentDownload = new SuccessfulDocumentDownload(200, emailInputStream, contentSizeInBytes, documentContent, "SampleFile");
        when(sendEmailDetails.getMaterialUrl()).thenReturn(of(materialUrl));
        final DownloadResponse downloadResponse = new DownloadResponse(successfulDocumentDownload);
        when(emailMaterialDownloader.downloadMaterial(notificationId, sendEmailDetails)).thenReturn(downloadResponse);

        when(microsoftOffice365ClientService.sendEmailByMicrosoftOffice365(templateId.toString(),
                sendEmailDetails.getSendToAddress(),
                notificationId.toString(), replyToAddressId.toString(), personalisationMap, "SampleFile", encodedDocumentContent)).thenReturn(office365EmailResponse);

        when(office365EmailResponse.getNotificationId()).thenReturn(externalNotificationId);
        when(logger.isInfoEnabled()).thenReturn(true);
        final JSONObject data = getJsonObjectData();
        when(office365EmailResponse.getData()).thenReturn(data);

        final Office365SenderResponse office365SenderResponse = (Office365SenderResponse) emailSender.send(notificationJobState);

        assertThat(office365SenderResponse.isSuccessful(), is(true));
        assertThat(office365SenderResponse.getExternalNotificationId(), is(externalNotificationId));

        verify(microsoftOffice365ClientService).sendEmailByMicrosoftOffice365(
                anyString(), anyString(), anyString(), anyString(), anyMap(), anyString(), anyString());
        verifyNoInteractions(govNotifyClientProvider);

        verify(logger).info("Sending email with template Id 'e15e255f-c84b-4557-a787-138f27a3f554' and notification Id '0bc5cacf-245b-488c-bc95-2ab182977d2d'");
    }

    @Test
    public void shouldReturnSuccessfulResponseThroughMicrosoftOffice365WhenSenderIsInListedDomainsWithNoAttachment() throws Exception {

        final UUID templateId = fromString("e15e255f-c84b-4557-a787-138f27a3f554");
        final UUID notificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final UUID externalNotificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final String sendToAddress = "a.tester@cjsm.net";
        final UUID replyToAddressId = fromString("9f476d4f-4069-444a-a511-905dcbb66910");
        final Map<String, Object> personalisationMap = new HashMap<>();

        final SendEmailDetails sendEmailDetails = mock(SendEmailDetails.class);
        final Office365EmailResponse office365EmailResponse = mock(Office365EmailResponse.class);
        final SendEmailDetailsJobState notificationJobState = mock(SendEmailDetailsJobState.class);

        when(notificationJobState.getNotificationId()).thenReturn(notificationId);
        when(notificationJobState.getTaskPayload()).thenReturn(sendEmailDetails);
        when(sendEmailDetails.getTemplateId()).thenReturn(templateId);
        when(sendEmailDetails.getSendToAddress()).thenReturn(sendToAddress);
        when(sendEmailDetails.getReplyToAddressId()).thenReturn(of(replyToAddressId));
        when(personalisationExtractor.extractFrom(sendEmailDetails)).thenReturn(personalisationMap);

        // No attachment
        when(sendEmailDetails.getMaterialUrl()).thenReturn(Optional.empty());
        when(sendEmailDetails.getFileId()).thenReturn(Optional.empty());

        when(microsoftOffice365ClientService.sendEmailByMicrosoftOffice365(templateId.toString(),
                sendEmailDetails.getSendToAddress(),
                notificationId.toString(),
                replyToAddressId.toString(),
                personalisationMap,
                "",
                "")
        ).thenReturn(office365EmailResponse);

        when(office365EmailResponse.getNotificationId()).thenReturn(externalNotificationId);
        when(logger.isInfoEnabled()).thenReturn(true);
        final JSONObject data = getJsonObjectData();
        when(office365EmailResponse.getData()).thenReturn(data);

        final Office365SenderResponse office365SenderResponse = (Office365SenderResponse) emailSender.send(notificationJobState);

        assertThat(office365SenderResponse.isSuccessful(), is(true));
        assertThat(office365SenderResponse.getExternalNotificationId(), is(externalNotificationId));

        verify(microsoftOffice365ClientService).sendEmailByMicrosoftOffice365(
                anyString(), anyString(), anyString(), anyString(), anyMap(), anyString(), anyString());
        verifyNoInteractions(govNotifyClientProvider);

        verify(logger).info("Sending email with template Id 'e15e255f-c84b-4557-a787-138f27a3f554' and notification Id '0bc5cacf-245b-488c-bc95-2ab182977d2d'");
    }

    @Test
    public void shouldReturnAnErrorIfTheEmailFileLinkDownloadedExceedsMaxLimitOf15MBAndSenderIsInListedDomains() throws Exception {
        final int httpResult = 413;
        final int contentSizeInBytes = 15728641;
        final String message = chomp(format("'Size too Big '"));
        final String expectedMessage = "Status code: 413 Document is larger than 15MB  with content size 15728641";

        final NotificationClientException notificationClientException = new NotificationClientException(message);
        setField(notificationClientException, "httpResult", httpResult);
        final UUID notificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final Map<String, Object> personalisationMap = new HashMap<>();
        final String materialUrl = "document url";
        final SendEmailDetails sendEmailDetails = mock(SendEmailDetails.class);
        final SendEmailDetailsJobState notificationJobState = mock(SendEmailDetailsJobState.class);

        final byte[] documentContentInByteArray = getFileContent("pdf/LargeSample.pdf");
        final InputStream emailInputStream = new ByteArrayInputStream(documentContentInByteArray);
        final SuccessfulDocumentDownload successfulDocumentDownload = new SuccessfulDocumentDownload(httpResult, emailInputStream, contentSizeInBytes, documentContentInByteArray, "LargeSample.pdf");

        when(notificationJobState.getNotificationId()).thenReturn(notificationId);
        when(notificationJobState.getTaskPayload()).thenReturn(sendEmailDetails);
        when(sendEmailDetails.getMaterialUrl()).thenReturn(of(materialUrl));
        when(sendEmailDetails.getMaterialUrl()).thenReturn(of(materialUrl));
        when(sendEmailDetails.getMaterialUrl()).thenReturn(of(materialUrl));
        when(logger.isErrorEnabled()).thenReturn(true);
        final DownloadResponse downloadResponse = new DownloadResponse(successfulDocumentDownload);
        when(emailMaterialDownloader.downloadMaterial(notificationId, sendEmailDetails)).thenReturn(downloadResponse);
        final ErrorResponse errorResponse = (ErrorResponse) emailSender.send(notificationJobState);

        assertThat(errorResponse.getStatusCode(), is(httpResult));
        assertThat(errorResponse.getErrorMessage(), is(expectedMessage));

        verifyNoInteractions(govNotifyClientProvider);
        verifyNoInteractions(microsoftOffice365ClientService);

        final InOrder inOrder = inOrder(logger);
        inOrder.verify(logger).error("An error was thrown while sending email for Notification: {} file size {}", notificationId, contentSizeInBytes);

    }

    @Test
    public void shouldSuccessfullySendEmailWithNoFileIdAndNoMaterialUrlAndDomainNotListed() throws Exception {

        final UUID templateId = fromString("e15e255f-c84b-4557-a787-138f27a3f554");
        final UUID notificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final UUID externalNotificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final String sendToAddress = "sendToAddress";
        final UUID replyToAddressId = fromString("9f476d4f-4069-444a-a511-905dcbb66910");
        final Map<String, Object> personalisationMap = new HashMap<>();

        final SendEmailDetails sendEmailDetails = mock(SendEmailDetails.class);
        final SendEmailResponse sendEmailResponse = mock(SendEmailResponse.class);
        final SendEmailDetailsJobState notificationJobState = mock(SendEmailDetailsJobState.class);

        when(notificationJobState.getNotificationId()).thenReturn(notificationId);
        when(notificationJobState.getTaskPayload()).thenReturn(sendEmailDetails);
        when(sendEmailDetails.getTemplateId()).thenReturn(templateId);
        when(sendEmailDetails.getSendToAddress()).thenReturn(sendToAddress);
        when(sendEmailDetails.getReplyToAddressId()).thenReturn(of(replyToAddressId));
        when(govNotifyClientProvider.getClient()).thenReturn(notificationClient);
        when(personalisationExtractor.extractFrom(sendEmailDetails)).thenReturn(personalisationMap);

        // No attachment
        when(sendEmailDetails.getMaterialUrl()).thenReturn(Optional.empty());
        when(sendEmailDetails.getFileId()).thenReturn(Optional.empty());

        when(notificationClient.sendEmail(templateId.toString(),
                sendEmailDetails.getSendToAddress(), personalisationExtractor.extractFrom(sendEmailDetails),
                notificationId.toString(), replyToAddressId.toString())).thenReturn(sendEmailResponse);

        when(sendEmailResponse.getNotificationId()).thenReturn(externalNotificationId);
        when(logger.isInfoEnabled()).thenReturn(true);

        final SenderResponse senderResponse = (SenderResponse) emailSender.send(notificationJobState);

        assertThat(senderResponse.isSuccessful(), is(true));
        assertThat(senderResponse.getExternalNotificationId(), is(externalNotificationId));

        verify(govNotifyClientProvider).getClient();
        verifyNoInteractions(microsoftOffice365ClientService);

        verify(logger).info("Sending email with template Id 'e15e255f-c84b-4557-a787-138f27a3f554' and notification Id '0bc5cacf-245b-488c-bc95-2ab182977d2d'");
    }

    @Test
    public void shouldEmailDomainIgnoreCaseSensitivityAndReturnSuccessfulResponseThroughMicrosoftOffice365() throws Exception {

        final UUID templateId = fromString("e15e255f-c84b-4557-a787-138f27a3f554");
        final UUID notificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final UUID externalNotificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final String sendToAddress = "a.tester@CJSM.net";
        final UUID replyToAddressId = fromString("9f476d4f-4069-444a-a511-905dcbb66910");
        final Map<String, Object> personalisationMap = new HashMap<>();

        final SendEmailDetails sendEmailDetails = mock(SendEmailDetails.class);
        final Office365EmailResponse office365EmailResponse = mock(Office365EmailResponse.class);
        final SendEmailDetailsJobState notificationJobState = mock(SendEmailDetailsJobState.class);

        when(notificationJobState.getNotificationId()).thenReturn(notificationId);
        when(notificationJobState.getTaskPayload()).thenReturn(sendEmailDetails);
        when(sendEmailDetails.getTemplateId()).thenReturn(templateId);
        when(sendEmailDetails.getSendToAddress()).thenReturn(sendToAddress);
        when(sendEmailDetails.getReplyToAddressId()).thenReturn(of(replyToAddressId));
        when(personalisationExtractor.extractFrom(sendEmailDetails)).thenReturn(personalisationMap);

        // No attachment
        when(sendEmailDetails.getMaterialUrl()).thenReturn(Optional.empty());
        when(sendEmailDetails.getFileId()).thenReturn(Optional.empty());

        when(microsoftOffice365ClientService.sendEmailByMicrosoftOffice365(templateId.toString(),
                sendEmailDetails.getSendToAddress(),
                notificationId.toString(),
                replyToAddressId.toString(),
                personalisationMap,
                "",
                "")
        ).thenReturn(office365EmailResponse);

        when(office365EmailResponse.getNotificationId()).thenReturn(externalNotificationId);
        when(logger.isInfoEnabled()).thenReturn(true);
        final JSONObject data = getJsonObjectData();
        when(office365EmailResponse.getData()).thenReturn(data);
        final Office365SenderResponse office365SenderResponse = (Office365SenderResponse) emailSender.send(notificationJobState);

        assertThat(office365SenderResponse.isSuccessful(), is(true));
        assertThat(office365SenderResponse.getExternalNotificationId(), is(externalNotificationId));

        verify(microsoftOffice365ClientService).sendEmailByMicrosoftOffice365(
                anyString(), anyString(), anyString(), anyString(), anyMap(), anyString(), anyString());
        verifyNoInteractions(govNotifyClientProvider);

        verify(logger).info("Sending email with template Id 'e15e255f-c84b-4557-a787-138f27a3f554' and notification Id '0bc5cacf-245b-488c-bc95-2ab182977d2d'");
    }

    @Test
    public void shouldshouldEmailDomainIgnoreCaseSensitivityAndReturnSuccessfulResponseThroughMicrosoftOffice365() throws Exception {

        final UUID templateId = fromString("e15e255f-c84b-4557-a787-138f27a3f554");
        final UUID notificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final UUID externalNotificationId = fromString("0bc5cacf-245b-488c-bc95-2ab182977d2d");
        final String sendToAddress = "a.tester@CJSM.net";
        final UUID replyToAddressId = fromString("9f476d4f-4069-444a-a511-905dcbb66910");
        final Map<String, Object> personalisationMap = new HashMap<>();

        final SendEmailDetails sendEmailDetails = mock(SendEmailDetails.class);
        final Office365EmailResponse office365EmailResponse = mock(Office365EmailResponse.class);
        final SendEmailDetailsJobState notificationJobState = mock(SendEmailDetailsJobState.class);

        when(notificationJobState.getNotificationId()).thenReturn(notificationId);
        when(notificationJobState.getTaskPayload()).thenReturn(sendEmailDetails);
        when(sendEmailDetails.getTemplateId()).thenReturn(templateId);
        when(sendEmailDetails.getSendToAddress()).thenReturn(sendToAddress);
        when(sendEmailDetails.getReplyToAddressId()).thenReturn(of(replyToAddressId));
        when(personalisationExtractor.extractFrom(sendEmailDetails)).thenReturn(personalisationMap);

        final String materialUrl = "document url";
        final int contentSizeInBytes = 123;
        final byte[] documentContent = "content".getBytes();
        final String encodedDocumentContent = Base64.encode(documentContent);

        final InputStream emailInputStream = new ByteArrayInputStream(documentContent);
        final SuccessfulDocumentDownload successfulDocumentDownload = new SuccessfulDocumentDownload(200, emailInputStream, contentSizeInBytes, documentContent, "SampleFile");
        when(sendEmailDetails.getMaterialUrl()).thenReturn(of(materialUrl));
        final DownloadResponse downloadResponse = new DownloadResponse(successfulDocumentDownload);
        when(emailMaterialDownloader.downloadMaterial(notificationId, sendEmailDetails)).thenReturn(downloadResponse);

        when(microsoftOffice365ClientService.sendEmailByMicrosoftOffice365(templateId.toString(),
                sendEmailDetails.getSendToAddress(),
                notificationId.toString(), replyToAddressId.toString(), personalisationMap, "SampleFile", encodedDocumentContent)).thenReturn(office365EmailResponse);

        when(office365EmailResponse.getNotificationId()).thenReturn(externalNotificationId);
        when(logger.isInfoEnabled()).thenReturn(true);

        final JSONObject data = getJsonObjectData();
        when(office365EmailResponse.getData()).thenReturn(data);

        final Office365SenderResponse office365SenderResponse = (Office365SenderResponse) emailSender.send(notificationJobState);

        assertThat(office365SenderResponse.isSuccessful(), is(true));
        assertThat(office365SenderResponse.getExternalNotificationId(), is(externalNotificationId));

        verify(microsoftOffice365ClientService).sendEmailByMicrosoftOffice365(
                anyString(), anyString(), anyString(), anyString(), anyMap(), anyString(), anyString());
        verifyNoInteractions(govNotifyClientProvider);

        verify(logger).info("Sending email with template Id 'e15e255f-c84b-4557-a787-138f27a3f554' and notification Id '0bc5cacf-245b-488c-bc95-2ab182977d2d'");
    }

    public static File getFileFrom(final String filePath) {
        return new File(getResource(filePath).getFile());
    }

    public static byte[] getFileContent(final String filePath) throws IOException {
        final File pdf = getFileFrom(filePath);
        return Files.readAllBytes(pdf.toPath());
    }

    private static JSONObject getJsonObjectData() {
        final JSONObject data = new JSONObject();
/*        data.put("htmlBody", "<html><head><title>Sample Title</title></head>"
                + "<body><h1>Welcome</h1><p>Hello World.</p></body></html>");*/
        data.put("htmlBody", "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\" lang=\"en\"> <head> <meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"> <meta content=\"telephone=no\" name=\"format-detection\"> <!-- need to add formatting for real phone numbers --> <meta name=\"viewport\" content=\"width=device-width\"> <title>Page title</title> <style type=\"text/css\"> @media only screen and (min-device-width: 581px) { .content { width: 580px !important; } } body { margin: 0 !important; } div[style*=\"margin: 16px 0\"] { margin: 0 !important; } </style> <!--[if gte mso 9]> <style type=\"text/css\"> li { margin-left: 4px !important; } table { mso-table-lspace: 0pt; mso-table-rspace: 0pt; } </style> <![endif]--> </head> <body style=\"font-family: Helvetica, Arial, sans-serif;font-size: 16px;margin: 0;color:#0b0c0c;\"> <span style=\"display: none;font-size: 1px;color: #fff; max-height: 0;\"></span> <table role=\"presentation\" width=\"100%\" style=\"border-collapse: collapse;min-width: 100%;width: 100% !important;\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"> <tbody> <tr> <td width=\"100%\" height=\"53\" bgcolor=\"#0b0c0c\"><!--[if (gte mso 9)|(IE)]> <table role=\"presentation\" width=\"580\" align=\"center\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse: collapse;width: 580px;\"> <tr> <td> <![endif]--> <table role=\"presentation\" width=\"100%\" style=\"border-collapse: collapse;max-width: 580px;\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" align=\"center\"> <tbody> <tr> <td width=\"70\" bgcolor=\"#0b0c0c\" valign=\"middle\"><a href=\"https://www.gov.uk\" title=\"Go to the GOV.UK homepage\" style=\"text-decoration: none;\"> <table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse: collapse;\"> <tbody> <tr> <td style=\"padding-left: 10px\"> <img src=\"https://static.notifications.service.gov.uk/images/gov.uk_logotype_crown.png\" alt=\" \" height=\"32\" border=\"0\" style=\"Margin-top: 4px;\"> </td> <td style=\"font-size: 28px; line-height: 1.315789474; Margin-top: 4px; padding-left: 10px;\"> <span style=\" font-family: Helvetica, Arial, sans-serif; font-weight: 700; color: #ffffff; text-decoration: none; vertical-align:top; display: inline-block; \">GOV.UK</span> </td> </tr> </tbody> </table> </a></td> </tr> </tbody> </table> <!--[if (gte mso 9)|(IE)]> </td> </tr> </table> <![endif]--></td> </tr> </tbody> </table> <table role=\"presentation\" class=\"content\" align=\"center\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse: collapse;max-width: 580px; width: 100% !important;\" width=\"100%\"> <tbody> <tr> <td width=\"10\" height=\"10\" valign=\"middle\"></td> <td><!--[if (gte mso 9)|(IE)]> <table role=\"presentation\" width=\"560\" align=\"center\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse: collapse;width: 560px;\"> <tr> <td height=\"10\"> <![endif]--> <table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse: collapse;\"> <tbody> <tr> <td bgcolor=\"#005EA5\" width=\"100%\" height=\"10\"></td> </tr> </tbody> </table> <!--[if (gte mso 9)|(IE)]> </td> </tr> </table> <![endif]--></td> <td width=\"10\" valign=\"middle\" height=\"10\"></td> </tr> </tbody> </table> <table role=\"presentation\" class=\"content\" align=\"center\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse: collapse;max-width: 580px; width: 100% !important;\" width=\"100%\"> <tbody> <tr> <td height=\"30\"><br> </td> </tr> <tr> <td width=\"10\" valign=\"middle\"><br> </td> <td style=\"font-family: Helvetica, Arial, sans-serif; font-size: 19px; line-height: 1.315789474; max-width: 560px;\"> <!--[if (gte mso 9)|(IE)]> <table role=\"presentation\" width=\"560\" align=\"center\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse: collapse;width: 560px;\"> <tr> <td style=\"font-family: Helvetica, Arial, sans-serif; font-size: 19px; line-height: 1.315789474;\"> <![endif]--> <p style=\"Margin: 0 0 20px 0; font-size: 19px; line-height: 25px; color: #0B0C0C;\"> \"html\": \"<p style=\\\"Margin: 0 0 20px 0; font-size: 19px; line-height: 25px; color: #0B0C0C;\\\">Follow the link below and download the attachment for Hearing Notification. </p><p style=\\\"Margin: 0 0 20px 0; font-size: 19px; line-height: 25px; color: #0B0C0C;\\\">Link: ((material_url))</p>\", \"id\": \"e4648583-eb0f-438e-aab5-5eff29f3f7b4\", \"postage\": null, \"subject\": \"Hearing Notification dated 25/01/2025 10:00 am\", \"type\": \"email\", \"version\": 3 } </p> <!--[if (gte mso 9)|(IE)]> </td> </tr> </table> <![endif]--></td> <td width=\"10\" valign=\"middle\"><br> </td> </tr> <tr> <td height=\"30\"><br> </td> </tr> </tbody> </table> This e-mail is private and is intended only for the addressee and any copy recipients. If you are not an intended recipient, please advise the sender immediately by reply e-mail and delete this message and any attachments without retaining a copy. Activity and use of this communication is monitored to secure their effective operation and for other lawful business purposes. Communications using these systems will also be monitored and may be recorded to secure effective operation and for other lawful business purposes. </body> </html>");
        data.put("subject", "Sample Subject");
        return data;
    }
}
