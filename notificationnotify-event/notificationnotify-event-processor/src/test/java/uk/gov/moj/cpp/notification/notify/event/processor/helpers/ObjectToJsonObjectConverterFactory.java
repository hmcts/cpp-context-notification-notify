package uk.gov.moj.cpp.notification.notify.event.processor.helpers;


import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;

import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;

public class ObjectToJsonObjectConverterFactory {

    public ObjectToJsonObjectConverter createNew() {

        final ObjectToJsonObjectConverter objectToJsonObjectConverter = new ObjectToJsonObjectConverter();

        setField(objectToJsonObjectConverter, "mapper", new ObjectMapperProducer().objectMapper());

        return objectToJsonObjectConverter;
    }
}
