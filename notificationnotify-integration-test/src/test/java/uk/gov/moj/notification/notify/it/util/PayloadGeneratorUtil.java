package uk.gov.moj.notification.notify.it.util;

import static java.util.UUID.fromString;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.CLIENT_CONTEXT;

import uk.gov.justice.json.schemas.domains.notificationnotify.Personalisation;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.json.JsonObject;

import com.fasterxml.jackson.databind.ObjectMapper;

public class PayloadGeneratorUtil {

    private static final UUID TEMPLATE_ID = fromString("e3bc6daf-1d9c-401c-beea-ec155212bd68");
    private static final String EMAIL_ADDRESS = "fred.bloggs@acme.com";
    private static final String REPLY_TO_ADDRESS = "the.grand.inquisitor@moj.gov.uk";
    private static final UUID REPLY_TO_ADDRESS_ID = fromString("9f476d4f-4069-444a-a511-905dcbb66910");
    private static final String personalizationName_1 = "name";
    private static final String personalizationValue_1 = "Allan";
    private static final String personalizationName_2 = "amount";
    private static final String personalizationValue_2 = "10,000,000";

    private static final String emailUrlName_3 = "material_url";

    public static String payloadWithPersonalisationWithoutMaterialUrl() {
        final Map<String, Object> personalisationMap = new HashMap<>();
        personalisationMap.put(personalizationName_1, personalizationValue_1);
        personalisationMap.put(personalizationName_2, personalizationValue_2);

        final JsonObject personalisationJson = getPersonalisationJsonObject(personalisationMap);

        return createObjectBuilder()
                .add("templateId", TEMPLATE_ID.toString())
                .add("sendToAddress", EMAIL_ADDRESS)
                .add("replyToAddress", REPLY_TO_ADDRESS)
                .add("replyToAddressId", REPLY_TO_ADDRESS_ID.toString())
                .add("personalisation", personalisationJson)
                .add("clientContext", CLIENT_CONTEXT)
                .build().toString();
    }

    public static String payloadWithPersonalisationWithMaterialUrl(final String materialLink) {
        final Map<String, Object> personalisationMap = new HashMap<>();
        personalisationMap.put(personalizationName_1, personalizationValue_1);
        personalisationMap.put(personalizationName_2, personalizationValue_2);
        personalisationMap.put(emailUrlName_3, materialLink);

        final JsonObject personalisationJson = getPersonalisationJsonObject(personalisationMap);

        return createObjectBuilder()
                .add("templateId", TEMPLATE_ID.toString())
                .add("sendToAddress", EMAIL_ADDRESS)
                .add("replyToAddress", REPLY_TO_ADDRESS)
                .add("replyToAddressId", REPLY_TO_ADDRESS_ID.toString())
                .add("personalisation", personalisationJson)
                .add("materialUrl", materialLink)
                .add("clientContext", CLIENT_CONTEXT)
                .build().toString();
    }

    public static String payloadWithPersonalisationWithFileId(final UUID fileId) {
        final Map<String, Object> personalisationMap = new HashMap<>();
        personalisationMap.put(personalizationName_1, personalizationValue_1);
        personalisationMap.put(personalizationName_2, personalizationValue_2);
        personalisationMap.put(emailUrlName_3, fileId);

        final JsonObject personalisationJson = getPersonalisationJsonObject(personalisationMap);

        return createObjectBuilder()
                .add("templateId", TEMPLATE_ID.toString())
                .add("sendToAddress", EMAIL_ADDRESS)
                .add("replyToAddress", REPLY_TO_ADDRESS)
                .add("replyToAddressId", REPLY_TO_ADDRESS_ID.toString())
                .add("personalisation", personalisationJson)
                .add("fileId", fileId.toString())
                .add("clientContext", CLIENT_CONTEXT)
                .build().toString();
    }

    public static String payloadWithoutPersonalisationWithFileId(final UUID fileId) {
        return createObjectBuilder()
                .add("templateId", TEMPLATE_ID.toString())
                .add("sendToAddress", EMAIL_ADDRESS)
                .add("replyToAddress", REPLY_TO_ADDRESS)
                .add("replyToAddressId", REPLY_TO_ADDRESS_ID.toString())
                .add("fileId", fileId.toString())
                .add("clientContext", CLIENT_CONTEXT)
                .build().toString();
    }

    public static String payloadWithoutPersonalisation() {
        return createObjectBuilder()
                .add("templateId", TEMPLATE_ID.toString())
                .add("sendToAddress", EMAIL_ADDRESS)
                .add("replyToAddress", REPLY_TO_ADDRESS)
                .build().toString();
    }

    public static JsonObject getPersonalisationJsonObject(final Map<String, Object> personalisationMap) {
        final Personalisation personalisation = new Personalisation(personalisationMap);

        final ObjectToJsonObjectConverter converter = new ObjectToJsonObjectConverter();
        final ObjectMapperProducer objectMapperProducer = new ObjectMapperProducer();
        final ObjectMapper mapper = objectMapperProducer.objectMapper();
        setField(converter, "mapper", mapper);
        return converter.convert(personalisation);
    }

}
