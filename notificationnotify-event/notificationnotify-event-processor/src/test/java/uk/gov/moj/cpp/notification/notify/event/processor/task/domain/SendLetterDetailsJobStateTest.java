package uk.gov.moj.cpp.notification.notify.event.processor.task.domain;

import static com.jayway.jsonassert.JsonAssert.with;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;

import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class SendLetterDetailsJobStateTest {

    private final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    @Test
    public void shouldParseToCorrectJson() throws Exception {

        final UUID notificationId = randomUUID();
        final String documentUrl = "document url";

        final SendLetterDetailsJobState sendLetterDetailsJobState = new SendLetterDetailsJobState(
                notificationId,
                new SendLetterDetails(documentUrl, "first")
        );

        final String json = objectMapper.writeValueAsString(sendLetterDetailsJobState);

        with(json)
                .assertThat("$.notificationId", is(notificationId.toString()))
                .assertThat("$.taskPayload.documentUrl", is(documentUrl))
        ;
    }
}
