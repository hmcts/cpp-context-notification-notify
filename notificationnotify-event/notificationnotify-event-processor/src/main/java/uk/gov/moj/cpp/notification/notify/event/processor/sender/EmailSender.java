package uk.gov.moj.cpp.notification.notify.event.processor.sender;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.chomp;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import uk.gov.justice.services.common.configuration.GlobalValue;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.moj.cpp.notification.notify.event.processor.client.GovNotifyClientProvider;
import uk.gov.moj.cpp.notification.notify.event.processor.client.MicroSoftOfficeClientNotificationException;
import uk.gov.moj.cpp.notification.notify.event.processor.client.Office365EmailResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.DownloadResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.ErrorResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.NotificationResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.Office365SenderResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.response.SenderResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.ExtractedSendEmailResponse;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.NotificationJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.SendEmailDetails;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.SendEmailDetailsJobState;
import uk.gov.moj.cpp.notification.notify.event.processor.util.PersonalisationExtractor;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

import java.io.IOException;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;


@ApplicationScoped
public class EmailSender implements NotificationSender {

    private static final String EMPTY_SPACE = "";
    private static final int NON_HTTP_ERROR_CONDITION = 999;
    private static final int BYTE_LENGTH_15_MB = 15728640;
    private static final int BYTE_LENGTH_2_MB = 2097152;

    private static final String AN_ERROR_WAS_THROWN_WHILE_SENDING_EMAIL = "An error was thrown while sending email";
    private static final String NO_REPLY_EMAIL_ADDRESS = "NOREPLY@noreply.com";

    @Inject
    @GlobalValue(key = "notify.email.sender.recipientDomainsToRouteThroughOffice365", defaultValue = "cjsm.net")
    private String office365DomainExtensions = "cjsm.net";

    @Inject
    private GovNotifyClientProvider govNotifyClientProvider;

    @Inject
    private PersonalisationExtractor personalisationExtractor;

    @Inject
    private EmailMaterialDownloader emailMaterialDownloader;

    @Inject
    private AttachmentsRetriever attachmentsRetriever;

    @Inject
    private MicrosoftOffice365ClientService microsoftOffice365ClientService;

    @Inject
    @SuppressWarnings("squid:S1312")
    private Logger logger;

    @Override
    @SuppressWarnings({"squid:S1166", "squid:S3776", "squid:S134"})
    //squid:S1166 - IOException is turned into ErrorResponseMessage, no need to rethrow
    public NotificationResponse send(final NotificationJobState<?> notificationJobState) {

        final SendEmailDetailsJobState sendEmailDetailsJobState = (SendEmailDetailsJobState) notificationJobState;
        final UUID notificationId = sendEmailDetailsJobState.getNotificationId();

        if (logger.isInfoEnabled()) {
            logger.info("Sending email with notification Id '{}'", notificationId);
        }

        final SendEmailDetails sendEmailDetails = sendEmailDetailsJobState.getTaskPayload();

        final Map<String, Object> personalisation = personalisationExtractor.extractFrom(sendEmailDetails);

        final NotificationResponse attachment;
        if (isMaterialUrlPresentAndValid(sendEmailDetails.getMaterialUrl())) {
            attachment = emailMaterialDownloader.downloadMaterial(notificationId, sendEmailDetails);
        } else {
            final Optional<UUID> fileId = sendEmailDetails.getFileId();
            if (fileId.isPresent()) {
                attachment = attachmentsRetriever.getAttachment(notificationId, fileId.get());
            } else {
                if (Stream.of(office365DomainExtensions.split(",")).anyMatch(sendEmailDetails.getSendToAddress().toLowerCase(Locale.ROOT)::endsWith)) {
                    return sendToMicrosoftOffice365Notify(notificationId, sendEmailDetails, personalisation);
                }
                return sendToGovNotify(notificationId, sendEmailDetails, personalisation);
            }
        }

        if (!attachment.isSuccessful()) {
            return attachment;
        }

        final DownloadResponse downloadMaterialResponse = (DownloadResponse) attachment;
        final int contentSize = downloadMaterialResponse.getSuccessfulDocumentDownload().contentSize();
        if (logger.isInfoEnabled()) {
            logger.info("EmailSender contentSize is '{}'", contentSize);
        }
        if (contentSize > BYTE_LENGTH_15_MB) {
            return handlePayloadBiggerThan15MB(notificationId, downloadMaterialResponse.getSuccessfulDocumentDownload().contentSize());
        } else {
            return sendWithAttachment(notificationId, sendEmailDetails, personalisation, downloadMaterialResponse, contentSize);
        }
    }

    private boolean shouldSendToOffice365Notify(final int contentSize, final String emailAddress) {
        return (contentSize > BYTE_LENGTH_2_MB || Stream.of(office365DomainExtensions.split(",")).anyMatch(emailAddress.toLowerCase(Locale.ROOT)::endsWith));
    }

    private boolean isMaterialUrlPresentAndValid(final Optional<String> materialUrl) {
        return materialUrl.isPresent() && !EMPTY_SPACE.equalsIgnoreCase(materialUrl.get());
    }

    private NotificationResponse sendWithAttachment(UUID notificationId, SendEmailDetails sendEmailDetails, Map<String, Object> personalisation, DownloadResponse downloadMaterialResponse, int contentSize) {

        if (shouldSendToOffice365Notify(contentSize, sendEmailDetails.getSendToAddress())) {

            return sendToMicrosoftOffice365Notify(
                    notificationId,
                    sendEmailDetails,
                    personalisation,
                    downloadMaterialResponse);
        }

        try {
            personalisation = personalisationExtractor.extractFrom(sendEmailDetails, downloadMaterialResponse.getSuccessfulDocumentDownload());
        } catch (final IOException ioe) {
            final String errorMessage = String.format("Exception while extracting personalisation for material payload with Notification: %s", notificationId);
            if (logger.isErrorEnabled()) {
                logger.error(errorMessage, ioe);
            }
            return new ErrorResponse(errorMessage, NON_HTTP_ERROR_CONDITION);
        }

        return sendToGovNotify(notificationId, sendEmailDetails, personalisation);
    }

    // squid:S2221 - Exception is handled by creating ErrorResponse object
    @SuppressWarnings("squid:S2221")
    private NotificationResponse sendToGovNotify(
            UUID notificationId,
            SendEmailDetails sendEmailDetails,
            Map<String, Object> personalisation) {

        final UUID templateId = sendEmailDetails.getTemplateId();

        if (logger.isInfoEnabled()) {
            logger.info(format("Sending email with template Id '%s' and notification Id '%s'", templateId, notificationId));
        }

        try {
            final NotificationClient notificationClient = govNotifyClientProvider.getClient();
            final String emailReplyToId = sendEmailDetails.getReplyToAddressId().map(UUID::toString).orElse("");
            final SendEmailResponse sendEmailResponse =
                    notificationClient.sendEmail(templateId.toString(),
                            sendEmailDetails.getSendToAddress(), personalisation, notificationId.toString(), emailReplyToId);
            ExtractedSendEmailResponse extractedSendEmailResponse = getExtractedSendEmailResponse(false,sendEmailDetails, sendEmailResponse);
            return new SenderResponse(sendEmailResponse.getNotificationId(), extractedSendEmailResponse);
        } catch (final NotificationClientException notificationClientException) {

            if (logger.isErrorEnabled()) {
                logger.error(AN_ERROR_WAS_THROWN_WHILE_SENDING_EMAIL, notificationClientException);
            }

            return new ErrorResponse(
                    format("Gov.Notify responded with '%s'", chomp(notificationClientException.getLocalizedMessage())),
                    notificationClientException.getHttpResult());

        } catch (final Exception e) {
            return handlePermanentFailure(notificationId, e);
        }
    }

    private static ExtractedSendEmailResponse getExtractedSendEmailResponse(boolean is365Email,
                                                                            SendEmailDetails sendEmailDetails,
                                                                            SendEmailResponse sendEmailResponse) {
        return new ExtractedSendEmailResponse(is365Email,
                sendEmailResponse.getSubject(),
                sendEmailResponse.getBody(),
                sendEmailResponse.getFromEmail().orElse(NO_REPLY_EMAIL_ADDRESS),
                sendEmailDetails.getSendToAddress());
    }

    private NotificationResponse sendToMicrosoftOffice365Notify(
            UUID notificationId,
            SendEmailDetails sendEmailDetails,
            Map<String, Object> personalisation) {

        return sendToMicrosoftOffice365Notify(notificationId, sendEmailDetails, personalisation, null);
    }

    private NotificationResponse sendToMicrosoftOffice365Notify(
            UUID notificationId,
            SendEmailDetails sendEmailDetails,
            Map<String, Object> personalisation,
            DownloadResponse downloadMaterialResponse) {
        if (logger.isInfoEnabled()) {
            logger.info("Sending email via MicrosoftOffice365 with notification Id '{}'", notificationId);
        }
        if (downloadMaterialResponse == null) {
            return sendWithAttachment(notificationId, sendEmailDetails, personalisation, "", "");
        }

        final byte[] documentContent = downloadMaterialResponse.getSuccessfulDocumentDownload().getBytes();
        final String encodedDocumentData = Base64.getEncoder().encodeToString(documentContent);
        final String fileName = downloadMaterialResponse.getSuccessfulDocumentDownload().getFileName();

        return sendWithAttachment(notificationId, sendEmailDetails, personalisation, encodedDocumentData, fileName);

    }

    @Inject
    private ObjectToJsonObjectConverter objectToJsonObjectConverter ;

    private NotificationResponse sendWithAttachment(
            final UUID notificationId,
            final SendEmailDetails sendEmailDetails,
            final Map<String, Object> personalisation,
            final String encodedDocumentData,
            final String fileName) {

        logger.info("EmailSender fileName is  '{}'", fileName);

        final UUID templateId = sendEmailDetails.getTemplateId();

        if (logger.isInfoEnabled()) {
            logger.info(format("Sending email with template Id '%s' and notification Id '%s'", templateId, notificationId));
        }

        final String emailReplyToId = sendEmailDetails.getReplyToAddressId().map(UUID::toString).orElse("");

        try {
            final Office365EmailResponse office365EmailResponse =
                    microsoftOffice365ClientService.sendEmailByMicrosoftOffice365(templateId.toString(), sendEmailDetails.getSendToAddress(),
                            notificationId.toString(), emailReplyToId, personalisation, fileName, encodedDocumentData);

            ExtractedSendEmailResponse  extractedSendEmailResponse =
                    getExtractedSendEmailResponseFromOffice365EmailResponse(true,sendEmailDetails, office365EmailResponse);

            return new Office365SenderResponse(office365EmailResponse.getNotificationId(), extractedSendEmailResponse);
        } catch (final MicroSoftOfficeClientNotificationException microSoftOfficeClientNotificationException) {

            if (logger.isErrorEnabled()) {
                logger.error(AN_ERROR_WAS_THROWN_WHILE_SENDING_EMAIL, microSoftOfficeClientNotificationException);
            }

            return new ErrorResponse(
                    format("MicroSoft Office 365 Notify responded with '%s'",
                            chomp(microSoftOfficeClientNotificationException.getLocalizedMessage())),
                    microSoftOfficeClientNotificationException.getHttpResult());

        } catch (final IOException ioe) {
            return handlePermanentFailure(notificationId, ioe);
        }
    }

    private ExtractedSendEmailResponse getExtractedSendEmailResponseFromOffice365EmailResponse(boolean is365Email,
                                                                                               SendEmailDetails sendEmailDetails,
                                                                                               Office365EmailResponse office365EmailResponse) {

        final JSONObject responseData = office365EmailResponse.getData();
        final String templateContent = responseData.isNull("templateContent") ? "" : responseData.getString("templateContent");
        final String htmlEmailBody = getEmailTemplateBody(templateContent);
        final String subject = responseData.isNull("subject") ? "" : responseData.getString("subject");
        final String emailSendToAddress = sendEmailDetails.getSendToAddress();
        final String emailReplyToAddress = sendEmailDetails.getReplyToAddress().orElse(NO_REPLY_EMAIL_ADDRESS);
        final Document document = Jsoup.parse(htmlEmailBody);
        final String emailBody = document.body().text();
        return new ExtractedSendEmailResponse(is365Email, subject, emailBody,
                emailReplyToAddress, emailSendToAddress);
    }

    public static String getEmailTemplateBody(final String str) {
        final String[] parts = str.split("\n");
        if (parts.length > 1) {
            return parts[1].trim();
        }
        return "";
    }

    private NotificationResponse handlePayloadBiggerThan15MB(final UUID notificationId,
                                                             final int contentSize) {
        if (logger.isErrorEnabled()) {
            logger.error(AN_ERROR_WAS_THROWN_WHILE_SENDING_EMAIL+ " for Notification: {} file size {}", notificationId, contentSize);
        }
        final String message = chomp(format("Status code: 413 Document is larger than 15MB  with content size %s", contentSize));
        return new ErrorResponse(message, 413);

    }

    private NotificationResponse handlePermanentFailure(UUID notificationId, Exception e) {
        if (logger.isErrorEnabled()) {
            logger.error("An unexpected error was thrown while sending email", e);
        }

        final String message = format(
                "Permanent failure, unexpected error while trying to deliver notification Id '%s', error message: '%s'",
                notificationId, e.getLocalizedMessage());

        return new ErrorResponse(message, NON_HTTP_ERROR_CONDITION);
    }

}
