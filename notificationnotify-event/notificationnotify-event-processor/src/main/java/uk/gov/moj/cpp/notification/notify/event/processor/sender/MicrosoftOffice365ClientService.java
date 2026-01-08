package uk.gov.moj.cpp.notification.notify.event.processor.sender;

import uk.gov.moj.cpp.notification.notify.event.processor.client.MicroSoftOfficeClientNotificationException;
import uk.gov.moj.cpp.notification.notify.event.processor.client.MicrosoftOffice365NotifyClientProvider;
import uk.gov.moj.cpp.notification.notify.event.processor.client.Office365EmailResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MicrosoftOffice365ClientService {


    private HttpConnectionHelper httpConnectionHelper;

    private static final Logger LOGGER = LoggerFactory.getLogger(MicrosoftOffice365ClientService.class);

    @Inject
    private MicrosoftOffice365NotifyClientProvider microsoftOffice365NotifyClientProvider;

    public MicrosoftOffice365ClientService() {
        this.httpConnectionHelper = new HttpConnectionHelper();
    }

    public MicrosoftOffice365ClientService(final HttpConnectionHelper httpConnectionHelper, final MicrosoftOffice365NotifyClientProvider microsoftOffice365NotifyClientProvider) {
        this.httpConnectionHelper = httpConnectionHelper;
        this.microsoftOffice365NotifyClientProvider = microsoftOffice365NotifyClientProvider;
    }

    @SuppressWarnings({"squid:S1160", "squid:S1162"})
    public Office365EmailResponse sendEmailByMicrosoftOffice365(
            final String templateId,
            final String emailAddress,
            final String reference,
            final String emailReplyToId,
            final Map<String, ?> personalisation,
            final String fileName,
            final String documentEncodedData
    ) throws IOException, MicroSoftOfficeClientNotificationException {

        LOGGER.info("MicrosoftOffice365ClientService fileName :: {}", fileName);
        final Map<String, Object> attachmentMap = new HashMap<>();
        attachmentMap.put("filename", fileName);
        attachmentMap.put("content", documentEncodedData);

        final JSONObject body = this.createBodyForPostRequest(templateId, emailAddress, reference, personalisation, attachmentMap);
        if (emailReplyToId != null && !emailReplyToId.isEmpty()) {
            body.put("replyToAddressId", emailReplyToId);
        }

        return httpConnectionHelper.getResponseCode(microsoftOffice365NotifyClientProvider.getOffice365NotifyApiEndpoint(), body, microsoftOffice365NotifyClientProvider.getSubscriptionKey());
    }

    private JSONObject createBodyForPostRequest(final String templateId, final String emailAddress,
                                                final String reference,  final Map<String, ?> personalisation,
                                                final Map<String, ?> attachmentMap) {
        final JSONObject body = new JSONObject();

        if (emailAddress != null && !emailAddress.isEmpty()) {
            body.put("emailAddress", emailAddress);
        }

        if (templateId != null && !templateId.isEmpty()) {
            body.put("templateId", templateId);
        }

        if (reference != null && !reference.isEmpty()) {
            body.put("reference", reference);
        }

        body.put("personalisation", personalisation);
        body.put("attachment", attachmentMap);

        return body;
    }

}
