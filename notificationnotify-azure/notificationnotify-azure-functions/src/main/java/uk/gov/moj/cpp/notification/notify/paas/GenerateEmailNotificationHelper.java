package uk.gov.moj.cpp.notification.notify.paas;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;

public class GenerateEmailNotificationHelper {

    private GenerateEmailNotificationHelper() {
        // prevent instantiation
    }

    public static HttpResponseMessage badRequestResponse(
        final HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context,
        final String message,
        final String s) {
            
        context.getLogger().log(Level.SEVERE, message);
        return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body(s+message).build();
    }

    public static RequestPayload getPayload(
            final HttpRequestMessage<Optional<String>> request,
            final String schemaFileName) throws IOException {

        final Schema schema = SchemaLoader.load(
                new JSONObject(
                        new JSONTokener(GenerateEmailNotification.class.getResourceAsStream("/json/schema/" + schemaFileName))
                )
        );

        schema.validate(new JSONObject(request.getBody().orElse("{}")));

        return new ObjectMapper().readValue(request.getBody().orElse("{}"), RequestPayload.class);
    }

    public static OutlookSettings getOutlookSettings(final String outlookSettingsJson) throws IOException {

        final Schema schema = SchemaLoader.load(
                new JSONObject(new JSONTokener(GenerateEmailNotification.class.getResourceAsStream("/json/schema/outlook-settings.json")))
        );

        schema.validate(new JSONObject(outlookSettingsJson));
        return new ObjectMapper().readValue(outlookSettingsJson, OutlookSettings.class);
    }

    public static RetrySettings getRetrySettings(final String retrySettingsJson) throws IOException {

        final Schema schema = SchemaLoader.load(
                new JSONObject(new JSONTokener(GenerateEmailNotification.class.getResourceAsStream("/json/schema/retry-settings.json")))
        );

        schema.validate(new JSONObject(retrySettingsJson));
        return new ObjectMapper().readValue(retrySettingsJson, RetrySettings.class);
    }

    public static String populateTemplate(final String template, final Map<String, ?> personalisation) {

        if (personalisation.isEmpty()) {
            return template;
        }

        final Map.Entry<String, ?> nextItem = personalisation.entrySet().iterator().next();
        personalisation.remove(nextItem.getKey());

        return populateTemplate(template.replace(
            String.format("((%s))", nextItem.getKey()), 
            nextItem.getValue().toString()), 
            personalisation
        );

    }

    public static String withoutFirstLine(final String str) {
        return str.substring(str.indexOf('\n')+1);
    }

    public static String getFirstLine(final String str) {
        final String[] parts = str.split("\n");
        return parts[0].trim();
    }


}

