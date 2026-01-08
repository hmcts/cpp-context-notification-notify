package uk.gov.moj.cpp.notification.notify.event.processor.task.domain;

import static com.jayway.jsonassert.JsonAssert.with;
import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;

import uk.gov.justice.json.schemas.domains.notificationnotify.Personalisation;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class SendEmailDetailJobStateTest {

    private final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    @Test
    public void shouldParseToCorrectJson() throws Exception {

        final UUID notificationId = randomUUID();
        final UUID templateId = randomUUID();
        final String sendToAddress = "send to address";
        final String replyToAddress = "replay to address";
        final UUID replyToAddressId = randomUUID();
        final UUID fileId = randomUUID();
        final String personalisationName = "Fred";
        final String materialUrl = "http://localhost";

        final Personalisation personalisation = new Personalisation.Builder()
                .withAdditionalProperty("Name", personalisationName)
                .build();

        final SendEmailDetails sendEmailDetails = new SendEmailDetails(
                templateId,
                sendToAddress,
                of(replyToAddress),
                of(replyToAddressId),
                of(personalisation),
                of(materialUrl),
                of(fileId));
        final SendEmailDetailsJobState sendEmailDetailsJobState = new SendEmailDetailsJobState(
                notificationId,
                sendEmailDetails
        );

        final String json = objectMapper.writeValueAsString(sendEmailDetailsJobState);

        with(json)
                .assertThat("$.notificationId", is(notificationId.toString()))
                .assertThat("$.taskPayload.templateId", is(templateId.toString()))
                .assertThat("$.taskPayload.sendToAddress", is(sendToAddress))
                .assertThat("$.taskPayload.replyToAddress", is(replyToAddress))
                .assertThat("$.taskPayload.materialUrl", is(materialUrl))
                .assertThat("$.taskPayload.fileId", is(fileId.toString()))
                .assertThat("$.taskPayload.personalisation.Name", is(personalisationName))
        ;
    }
}