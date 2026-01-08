package uk.gov.moj.cpp.notification.notify.event.processor.sender;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.notification.notify.event.processor.client.MicroSoftOfficeClientNotificationException;
import uk.gov.moj.cpp.notification.notify.event.processor.client.MicrosoftOffice365NotifyClientProvider;
import uk.gov.moj.cpp.notification.notify.event.processor.client.Office365EmailResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.json.Json;
import javax.json.JsonObject;

import org.jose4j.base64url.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MicrosoftOffice365ClientServiceTest {

    @Mock
    HttpConnectionHelper httpConnectionHelper;

    @Mock
    private MicrosoftOffice365NotifyClientProvider microsoftOffice365NotifyClientProvider;

    @BeforeEach
    public void initMocks() {
        when(microsoftOffice365NotifyClientProvider.getOffice365NotifyApiEndpoint()).thenReturn("https://localhost/generate-notification-email");
    }

    @Test
    public void testSendEmailByMicrosoftOffice365() throws IOException,  MicroSoftOfficeClientNotificationException {
        final UUID notificationId = UUID.randomUUID();
        final String templateId = "templateId";
        final String emailId = "abc@xyz.com";
        final String reference = "reference";
        final String materialUrl = "/material/id";
        final Map<String, ?> personalisation = new HashMap<>();

        final JsonObject responseObject  = Json.createObjectBuilder()
                .add("reference", notificationId.toString())
                .build();
        final byte[] bytes = "content".getBytes();
        final String encodeDocumentData = Base64.encode(bytes);
        final String fileName ="sampleFileName.pdf";
        final Office365EmailResponse office365EmailResponse = new Office365EmailResponse(responseObject.toString());
        when(httpConnectionHelper.getResponseCode(any(), any(), any())).thenReturn(office365EmailResponse);
        MicrosoftOffice365ClientService microsoftOffice365ClientService = new MicrosoftOffice365ClientService(httpConnectionHelper, microsoftOffice365NotifyClientProvider);
        final Office365EmailResponse actualResponse =microsoftOffice365ClientService.sendEmailByMicrosoftOffice365(templateId,emailId, reference, null, personalisation, fileName, encodeDocumentData);
        assertThat(actualResponse.getNotificationId() , is(notificationId));
    }

    @Test
    public void testSendEmailByMicrosoftOffice365ExpectMicroSoftOfficeClientNotificationException() throws IOException,  MicroSoftOfficeClientNotificationException {
        final String templateId = "templateId";
        final String emailId = "abc@xyz.com";
        final String reference = "reference";
        final Map<String, ?> personalisation = new HashMap<>();

        final byte[] bytes = "content".getBytes();
        final String encodeDocumentData = Base64.encode(bytes);
        final String fileName ="sampleFileName.pdf";
        doThrow(new MicroSoftOfficeClientNotificationException("Unable to get response from Office 365")).when(httpConnectionHelper).getResponseCode(any(), any(), any());
        MicrosoftOffice365ClientService microsoftOffice365ClientService = new MicrosoftOffice365ClientService(httpConnectionHelper, microsoftOffice365NotifyClientProvider);
        assertThrows(MicroSoftOfficeClientNotificationException.class, () -> microsoftOffice365ClientService.sendEmailByMicrosoftOffice365(templateId,emailId, reference, null, personalisation, fileName, encodeDocumentData));
    }

    @Test
    public void testSendEmailByMicrosoftOffice365ExpectIOException() throws IOException,  MicroSoftOfficeClientNotificationException {
        final String templateId = "templateId";
        final String emailId = "abc@xyz.com";
        final String reference = "reference";
        final Map<String, ?> personalisation = new HashMap<>();
        final byte[] bytes = "content".getBytes();
        final String encodeDocumentData = Base64.encode(bytes);
        final String fileName ="sampleFileName.pdf";

        doThrow(new IOException()).when(httpConnectionHelper).getResponseCode(any(), any(), any());
        MicrosoftOffice365ClientService microsoftOffice365ClientService = new MicrosoftOffice365ClientService(httpConnectionHelper, microsoftOffice365NotifyClientProvider);
        assertThrows(IOException.class, () -> microsoftOffice365ClientService.sendEmailByMicrosoftOffice365(templateId,emailId, reference, null, personalisation, fileName ,encodeDocumentData));
    }
}
