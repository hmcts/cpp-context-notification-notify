package uk.gov.moj.cpp.notification.notify.event.processor;

import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.test.utils.core.messaging.MetadataBuilderFactory.metadataWithRandomUUID;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.STARTED;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.SEND_EMAIL;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.SEND_LETTER;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.jobstore.api.ExecutionService;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;

import java.time.ZonedDateTime;
import java.util.UUID;

import javax.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
public class NotificationNotifyEventProcessorTest {
    @Mock
    private UtcClock utcClock;
    @Spy
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;
    @Spy
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;
    @Mock
    private ExecutionService executionService;
    @Mock
    private Logger logger;
    @InjectMocks
    private NotificationNotifyEventProcessor eventProcessor;
    @Captor
    private ArgumentCaptor<ExecutionInfo> executionInfoCaptor;

    @BeforeEach
    public void setup() {
        setField(this.jsonObjectToObjectConverter, "objectMapper", new ObjectMapperProducer().objectMapper());
        setField(this.objectToJsonObjectConverter, "mapper", new ObjectMapperProducer().objectMapper());
    }

    @Test
    public void shouldCreateSendEmailWithNoMaterialLinkNotificationJob() {

        final UUID notificationId = randomUUID();
        final UUID templateId = randomUUID();
        final UUID replyToAddressId = randomUUID();
        final String replyToAddress = "replyToAddress";
        final String sendToAddress = "sendToAddress";

        final JsonObject payload = createObjectBuilder()
                .add("notificationId", notificationId.toString())
                .add("templateId", templateId.toString())
                .add("replyToAddress", replyToAddress)
                .add("replyToAddressId", replyToAddressId.toString())
                .add("sendToAddress", sendToAddress)
                .build();
        final JsonEnvelope event = envelopeFrom(metadataWithRandomUUID("notificationnotify.events.notification-queued"), payload);

        final ZonedDateTime nextStartTime = new UtcClock().now();
        when(utcClock.now()).thenReturn(nextStartTime);

        eventProcessor.createNotificationJob(event);

        verify(executionService).executeWith(executionInfoCaptor.capture());
        final ExecutionInfo executionInfo = executionInfoCaptor.getValue();
        assertThat(executionInfo.getNextTask(), is(SEND_EMAIL.getTaskName()));
        assertThat(executionInfo.getExecutionStatus(), is(STARTED));
        assertThat(executionInfo.getNextTaskStartTime(), is(nextStartTime));
        final JsonObject jobData = executionInfo.getJobData();
        assertThat(jobData.getString("notificationId"), is(notificationId.toString()));
        assertThat(jobData.getJsonObject("taskPayload").getString("templateId"), is(templateId.toString()));
        assertThat(jobData.getJsonObject("taskPayload").getString("sendToAddress"), is(sendToAddress));
        assertThat(jobData.getJsonObject("taskPayload").getString("replyToAddress"), is(replyToAddress));
        assertThat(jobData.getJsonObject("taskPayload").getString("replyToAddressId"), is(replyToAddressId.toString()));
        verify(logger).info("Adding notification {} to the jobstore for later execution", notificationId);
    }

    @Test
    public void shouldCreateSendEmailWithMaterialLinkNotificationJob() {

        final UUID notificationId = randomUUID();
        final UUID templateId = randomUUID();
        final UUID replyToAddressId = randomUUID();
        final String materialUrl = "http://linkToDownload";
        final String replyToAddress = "replyToAddress";
        final String sendToAddress = "sendToAddress";

        final JsonObject payload = createObjectBuilder()
                .add("notificationId", notificationId.toString())
                .add("templateId", templateId.toString())
                .add("materialUrl", materialUrl)
                .add("replyToAddress", replyToAddress)
                .add("replyToAddressId", replyToAddressId.toString())
                .add("sendToAddress", sendToAddress)
                .build();
        final JsonEnvelope event = envelopeFrom(metadataWithRandomUUID("notificationnotify.events.notification-queued"), payload);

        final ZonedDateTime nextStartTime = new UtcClock().now();
        when(utcClock.now()).thenReturn(nextStartTime);

        eventProcessor.createNotificationJob(event);

        verify(executionService).executeWith(executionInfoCaptor.capture());
        final ExecutionInfo executionInfo = executionInfoCaptor.getValue();
        assertThat(executionInfo.getNextTask(), is(SEND_EMAIL.getTaskName()));
        assertThat(executionInfoCaptor.getValue().getExecutionStatus(), is(STARTED));
        assertThat(executionInfoCaptor.getValue().getNextTaskStartTime(), is(nextStartTime));
        final JsonObject jobData = executionInfo.getJobData();
        assertThat(jobData.getString("notificationId"), is(notificationId.toString()));
        assertThat(jobData.getJsonObject("taskPayload").getString("templateId"), is(templateId.toString()));
        assertThat(jobData.getJsonObject("taskPayload").getString("sendToAddress"), is(sendToAddress));
        assertThat(jobData.getJsonObject("taskPayload").getString("replyToAddress"), is(replyToAddress));
        assertThat(jobData.getJsonObject("taskPayload").getString("replyToAddressId"), is(replyToAddressId.toString()));
        assertThat(jobData.getJsonObject("taskPayload").getString("materialUrl"), is(materialUrl));

        verify(logger).info("Adding notification {} to the jobstore for later execution", notificationId);
    }

    @Test
    public void shouldCreateSendEmailWithFileIdNotificationJob() {

        final UUID notificationId = randomUUID();
        final UUID templateId = randomUUID();
        final UUID replyToAddressId = randomUUID();
        final UUID fileId = randomUUID();
        final String replyToAddress = "replyToAddress";
        final String sendToAddress = "sendToAddress";

        final JsonObject payload = createObjectBuilder()
                .add("notificationId", notificationId.toString())
                .add("templateId", templateId.toString())
                .add("fileId", fileId.toString())
                .add("replyToAddress", replyToAddress)
                .add("replyToAddressId", replyToAddressId.toString())
                .add("sendToAddress", sendToAddress)
                .build();
        final JsonEnvelope event = envelopeFrom(metadataWithRandomUUID("notificationnotify.events.notification-queued"), payload);

        final ZonedDateTime nextStartTime = new UtcClock().now();
        when(utcClock.now()).thenReturn(nextStartTime);

        eventProcessor.createNotificationJob(event);

        verify(executionService).executeWith(executionInfoCaptor.capture());
        final ExecutionInfo executionInfo = executionInfoCaptor.getValue();
        assertThat(executionInfo.getNextTask(), is(SEND_EMAIL.getTaskName()));
        assertThat(executionInfoCaptor.getValue().getExecutionStatus(), is(STARTED));
        assertThat(executionInfoCaptor.getValue().getNextTaskStartTime(), is(nextStartTime));
        final JsonObject jobData = executionInfo.getJobData();
        assertThat(jobData.getString("notificationId"), is(notificationId.toString()));
        assertThat(jobData.getJsonObject("taskPayload").getString("templateId"), is(templateId.toString()));
        assertThat(jobData.getJsonObject("taskPayload").getString("sendToAddress"), is(sendToAddress));
        assertThat(jobData.getJsonObject("taskPayload").getString("replyToAddress"), is(replyToAddress));
        assertThat(jobData.getJsonObject("taskPayload").getString("replyToAddressId"), is(replyToAddressId.toString()));
        assertThat(jobData.getJsonObject("taskPayload").getString("fileId"), is(fileId.toString()));

        verify(logger).info("Adding notification {} to the jobstore for later execution", notificationId);
    }

    @Test
    public void shouldCreateSendLetterNotificationJob() throws Exception {
        final UUID notificationId = randomUUID();
        final String letterUrl = "http://randomUrl.com";
        final JsonObject payload = createObjectBuilder()
                .add("notificationId", notificationId.toString())
                .add("letterUrl", letterUrl)
                .build();
        final JsonEnvelope event = envelopeFrom(metadataWithRandomUUID("notificationnotify.events.letter-queued"), payload);

        final ZonedDateTime nextStartTime = new UtcClock().now();
        when(utcClock.now()).thenReturn(nextStartTime);

        eventProcessor.createLetterNotificationJob(event);

        verify(executionService).executeWith(executionInfoCaptor.capture());
        final ExecutionInfo executionInfo = this.executionInfoCaptor.getValue();
        assertThat(executionInfo.getNextTask(), is(SEND_LETTER.getTaskName()));
        assertThat(executionInfo.getExecutionStatus(), is(STARTED));
        assertThat(executionInfo.getNextTaskStartTime(), is(nextStartTime));

        final JsonObject jobData = executionInfo.getJobData();
        assertThat(jobData.getString("notificationId"), is(notificationId.toString()));
        assertThat(jobData.getJsonObject("taskPayload").getString("documentUrl"), is(letterUrl));
        assertThat(jobData.getJsonObject("taskPayload").getString("postage"), is(""));

        verify(logger).info("Adding notification {} to the jobstore for later execution", notificationId);
    }

    @Test
    public void shouldCreateSendFirstClassLetterNotificationJob() throws Exception {
        final UUID notificationId = randomUUID();
        final String letterUrl = "http://randomUrl.com";

        final JsonObject payload = createObjectBuilder()
                .add("notificationId", notificationId.toString())
                .add("letterUrl", letterUrl)
                .build();
        final JsonEnvelope event = envelopeFrom(metadataWithRandomUUID("notificationnotify.events.first-class-letter-queued"), payload);

        final ZonedDateTime nextStartTime = new UtcClock().now();
        when(utcClock.now()).thenReturn(nextStartTime);

        eventProcessor.createLetterNotificationJobForFirstClass(event);

        verify(executionService).executeWith(executionInfoCaptor.capture());
        final ExecutionInfo executionInfo = this.executionInfoCaptor.getValue();
        assertThat(executionInfo.getNextTask(), is(SEND_LETTER.getTaskName()));
        assertThat(executionInfo.getExecutionStatus(), is(STARTED));
        assertThat(executionInfo.getNextTaskStartTime(), is(nextStartTime));

        final JsonObject jobData = executionInfo.getJobData();
        assertThat(jobData.getString("notificationId"), is(notificationId.toString()));
        assertThat(jobData.getJsonObject("taskPayload").getString("documentUrl"), is(letterUrl));
        assertThat(jobData.getJsonObject("taskPayload").getString("postage"), is("first"));

        verify(logger).info("Adding notification {} to the jobstore for later execution", notificationId);
    }

}
