package uk.gov.moj.cpp.notification.notify.paas;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.nio.charset.StandardCharsets.UTF_8;
import static uk.gov.moj.cpp.notification.notify.paas.EnvironmentHelper.DISABLE_SEND_EMAIL;
import static uk.gov.moj.cpp.notification.notify.paas.EnvironmentHelper.TEST_RECIPIENT_EMAIL;
import static uk.gov.moj.cpp.notification.notify.paas.EnvironmentHelper.getEnvironmentVariableForRetrySettings;
import static uk.gov.moj.cpp.notification.notify.paas.EnvironmentHelper.getEnvironmentVariableForSmtpSettings;
import static uk.gov.moj.cpp.notification.notify.paas.EnvironmentHelper.getEnvironmentVariableForDisableSendEmail;
import static uk.gov.moj.cpp.notification.notify.paas.EnvironmentHelper.getEnvironmentVariableForTestRecipientEmail;
import static uk.gov.moj.cpp.notification.notify.paas.GenerateEmailNotificationHelper.badRequestResponse;
import static uk.gov.moj.cpp.notification.notify.paas.GenerateEmailNotificationHelper.getFirstLine;
import static uk.gov.moj.cpp.notification.notify.paas.GenerateEmailNotificationHelper.getOutlookSettings;
import static uk.gov.moj.cpp.notification.notify.paas.GenerateEmailNotificationHelper.getPayload;
import static uk.gov.moj.cpp.notification.notify.paas.GenerateEmailNotificationHelper.getRetrySettings;
import static uk.gov.moj.cpp.notification.notify.paas.GenerateEmailNotificationHelper.populateTemplate;
import static uk.gov.moj.cpp.notification.notify.paas.GenerateEmailNotificationHelper.withoutFirstLine;
import static uk.gov.moj.cpp.notification.notify.paas.ResponsePayload.createResponsePayloadBuilder;
import static uk.gov.moj.cpp.notification.notify.paas.SendMail.NOTIFICATION_ID;
import static uk.gov.moj.cpp.notification.notify.paas.SendMail.sendMail;

import uk.gov.moj.cpp.notification.notify.paas.service.AzureCloudStorageService;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Logger;

import javax.mail.MessagingException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.BlobInput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.annotation.StorageAccount;
import com.microsoft.azure.storage.StorageException;
import org.everit.json.schema.ValidationException;
import org.json.JSONException;

/**
 * Azure Functions with HTTP Trigger.
 */
@SuppressWarnings({"squid:S1312","squid:S3776"})
public class GenerateEmailNotification {

    public static final String AN_UNKNOWN_ERROR_HAS_OCCURRED = "An unknown error has occurred.";

    /**
     * This function listens at endpoint "/api/HttpTrigger-Java". Two ways to invoke it using "curl"
     * command in bash: 1. curl -d "HTTP Body" {your host}/api/HttpTrigger-Java&code={your function
     * key} 2. curl "{your host}/api/HttpTrigger-Java?name=HTTP%20Query&code={your function key}"
     * Function Key is not needed when running locally, it is used to invoke function deployed to
     * Azure. More details: https://aka.ms/functions_authorization_keys
     */
    @FunctionName("generate-notification-email")
    @StorageAccount("AzureWebJobsStorage")
    @SuppressWarnings("squid:S1166, squid:S1312")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.FUNCTION) final HttpRequestMessage<Optional<String>> request,
            @BindingName("templateId") final String templateId,
            @BlobInput(
                    name = "template",
                    dataType = "binary",
                    path = "email-templates/{templateId}.template")
                    byte[] template,
            @BlobInput(
                    name = "html",
                    dataType = "binary",
                    path = "email-templates/gov-notify-design.html")
                    byte[] html,
            final ExecutionContext context) throws InterruptedException {

        final Logger logger = context.getLogger();
        logger.info(() -> "Java HTTP trigger processed a request for template: " + templateId);
        final StringBuilder notificationId = new StringBuilder();
        final OutlookSettings outlookSettings;
        final RetrySettings retrySettings;
        final boolean disableSendEmail;
        final String testRecipientEmail;
        final AzureCloudStorageService azureCloudStorageService = new AzureCloudStorageService();

        try {
            final String outlookSettingsAsJson = getEnvironmentVariableForSmtpSettings();
            outlookSettings = getOutlookSettings(outlookSettingsAsJson);
            final String retrySettingsAsJson = getEnvironmentVariableForRetrySettings();
            retrySettings = getRetrySettings(retrySettingsAsJson);
            disableSendEmail = Boolean.parseBoolean(getEnvironmentVariableForDisableSendEmail());
            testRecipientEmail = getEnvironmentVariableForTestRecipientEmail();
        } catch (final IOException e) {
            logger.severe(() -> "An unknown error has occurred: " + e);
            return badRequestResponse(request, context, e.getMessage(), AN_UNKNOWN_ERROR_HAS_OCCURRED);
        }

        final String subject;
        final String populatedHtmlString;
        final RequestPayload requestPayload;
        final String populatedTemplateString;
        try {
            requestPayload = getPayload(request, "email-details.json");
            final String templateString = withoutFirstLine(new String(template, UTF_8));
            final String htmlString = new String(html, UTF_8);
            populatedTemplateString = populateTemplate(templateString, requestPayload.getPersonalisation());
            subject = getFirstLine(populatedTemplateString);
            populatedHtmlString = htmlString.replace("((EMAIL_BODY))", withoutFirstLine(populatedTemplateString));
            notificationId.setLength(0);
            notificationId.append(requestPayload.getReference());

            logger.info(() -> NOTIFICATION_ID + " " + notificationId + "Email subject is: " + subject);
            logger.info(() -> NOTIFICATION_ID + " " + notificationId + "Email html content is: " + populatedHtmlString);
            logger.info(() -> NOTIFICATION_ID + " " + notificationId + "Email template content is: " + populatedTemplateString);

        } catch (final JsonParseException e) {
            logger.severe(() -> "Invalid JSON("+notificationId+"): " + e);
            return badRequestResponse(request, context, e.getMessage(), "Invalid JSON.");
        } catch (final JsonMappingException e) {
            logger.severe(() -> "Unable to convert JSON payload("+notificationId+"): " + e);
            return badRequestResponse(request, context, e.getMessage(), "Unable to convert JSON payload.");
        }catch (final IOException e) {
            logger.severe(() -> "An unknown error has occurred: " + e);
            return badRequestResponse(request, context, e.getMessage(), AN_UNKNOWN_ERROR_HAS_OCCURRED);
        }

        final int maxRetryAttempts = retrySettings.getRetryDefinitions().stream().map(RetryDefinition::getRetryNumber).mapToInt(v -> v).max().getAsInt() +1;
        int retryAttempts = 0;

        do {
            try {
                final int serverId = azureCloudStorageService.getNextSmtpServer(logger);
                final OutlookCredentials selectedServer =  outlookSettings.getOutlookCredentials().stream().filter(server -> server.getServerId()==serverId).findFirst().orElse(null);

                if (disableSendEmail) {
                    if (!isNullOrEmpty(testRecipientEmail)) {
                        logger.info(() -> "Email will be sent to a TEST_RECIPIENT_EMAIL address. " + TEST_RECIPIENT_EMAIL + " Environment variable is set to " + testRecipientEmail);
                        sendMail( subject, populatedHtmlString, testRecipientEmail, requestPayload.getAttachment(),
                                requestPayload.getReference(), selectedServer, context);
                    } else {
                        logger.info(() -> "Email sending is disabled. Email will not be sent. " + DISABLE_SEND_EMAIL + " Environment variable is set to " + disableSendEmail);
                    }
                } else {
                    sendMail( subject, populatedHtmlString, requestPayload.getEmailAddress(), requestPayload.getAttachment(),
                            requestPayload.getReference(), selectedServer, context);
                }

                return request.createResponseBuilder(HttpStatus.OK)
                        .body(createResponsePayloadBuilder()
                                .withHtmlBody(populatedHtmlString)
                                .withReference(requestPayload.getReference())
                                .withTemplateId(templateId)
                                .withSubject(subject)
                                .withTemplateContent(populatedTemplateString)
                                .build())
                        .build();

            } catch (final ValidationException e) {
                logger.severe(() -> "Schema validation failed (" + notificationId + "): " + e);
                return badRequestResponse(request, context, e.getMessage(), "Schema validation failed.");
            } catch (final IOException | JSONException e) {
                logger.severe(() -> "An unknown error has occurred(" + notificationId + "): " + e);
                return badRequestResponse(request, context, e.getMessage(), AN_UNKNOWN_ERROR_HAS_OCCURRED);
            } catch (StorageException e) {
                logger.severe(() -> "Error while retrieving SmtpSettings from Azure Storage: " + notificationId + e);
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR).body("Error while retrieving SmtpSettings from Azure Storage :  " + " Errormsg : " + e).build();
            } catch (MessagingException e) {
                final int retryAttempt = retryAttempts;
                logger.severe(() -> "MessagingException has occurred on retryAttempt(" + notificationId + "): " + retryAttempt + " " + e);
                if (++retryAttempts < maxRetryAttempts) {
                    final int retryDelay = retrySettings.getRetryDefinitions().stream().filter(retryDefinition -> retryDefinition.getRetryNumber() == retryAttempt + 1)
                            .findFirst().orElseGet(RetryDefinition::new).getDuration();
                    logger.severe(() -> "waiting " + retryDelay + " seconds for Retry Attempt(" + notificationId + ") : " + (retryAttempt + 1));
                    Thread.sleep(retryDelay * 1000L);
                } else {
                    logger.severe(() -> "MessagingException after all retry attempts(" + notificationId + "): " + (retryAttempt + 1) + " " + e);
                    return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR).body("MessagingException after all retry attempts:  " + retryAttempts + " Errormsg : " + e).build();
                }
            }
        } while (true);
    }

}
