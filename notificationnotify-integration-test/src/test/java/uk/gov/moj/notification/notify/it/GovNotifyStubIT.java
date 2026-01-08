package uk.gov.moj.notification.notify.it;

import static java.util.Optional.of;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.justice.services.test.utils.common.host.TestHostProvider.getHost;
import static uk.gov.justice.services.test.utils.core.http.BaseUriProvider.getBaseUri;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubDocumentDownload;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyCheckStatusWhenPermanentFailure;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyCheckStatusWhenTemporaryFailure;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyCheckStatusWithStatusDelivered;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifySendLetterWithStatusDelivered;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyToReturnCreatedAsStatusWhenCheckingStatus;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyToReturnPermanentFailureAsStatusWhenCheckingStatus;
import static uk.gov.moj.notification.notify.it.stub.NotificationStubUtils.stubGovNotifyToReturnTemporaryFailureWhenCheckingStatus;
import static uk.gov.moj.notification.notify.it.stub.SendEmailStub.sendEmailStub;
import static uk.gov.moj.notification.notify.it.stub.SuccessfulGovNotifyResponseBuilder.aSuccessfulResponse;
import static uk.gov.moj.notification.notify.it.util.ResourceLoader.getBase64EncodedFileContent;
import static uk.gov.moj.notification.notify.it.util.ResourceLoader.getFileContent;
import static uk.gov.moj.notification.notify.it.util.ResourceLoader.getFileFrom;

import uk.gov.service.notify.LetterResponse;
import uk.gov.service.notify.Notification;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.SendEmailResponse;

import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


public class GovNotifyStubIT {

    private static final String BASE_URI = getBaseUri();
    private static final UUID REFERENCE = randomUUID();
    private static final UUID TEMPLATE_ID = randomUUID();
    private static final UUID NOTIFICATION_ID = randomUUID();
    private static final String EMAIL_ADDRESS = "fred.bloggs@acme.com";
    private static final UUID USER_ID = fromString("d43135e4-fff3-45df-9a7e-bc7018a4a589");
    private static final UUID REPLY_TO_ADDRESS_ID = randomUUID();
    private static final String API_KEY = "allans_test_api_key-" + randomUUID() + "-" + randomUUID();

    @BeforeEach
    public void before() {
        WireMock.configureFor(getHost(), 8080);
        WireMock.reset();
    }

    @Test
    public void shouldEmulateTheGovDotNotifyCheckStatusEndpointForDelivered() throws Exception {
        final UUID cppNotificationId = randomUUID();
        final UUID govNotifyNotificationId = randomUUID();
        stubGovNotifyCheckStatusWithStatusDelivered(cppNotificationId, govNotifyNotificationId);

        final NotificationClient client = new NotificationClient(API_KEY, BASE_URI);

        final Notification response = client.getNotificationById(govNotifyNotificationId.toString());
        assertThat(govNotifyNotificationId, is(response.getId()));
        assertThat(response.getStatus(), is("delivered"));
    }

    @Test
    public void shouldEmulateTheGovDotNotifyCheckStatusEndpointForPermanentFailure() throws Exception {
        final UUID cppNotificationId = randomUUID();
        final UUID govNotifyNotificationId = randomUUID();
        stubGovNotifyCheckStatusWhenPermanentFailure(cppNotificationId, govNotifyNotificationId);

        final NotificationClient client = new NotificationClient(API_KEY, BASE_URI);

        final Notification response = client.getNotificationById(govNotifyNotificationId.toString());
        assertThat(govNotifyNotificationId, is(response.getId()));
        assertThat(response.getStatus(), is("permanent-failure"));
    }

    @Test
    public void shouldEmulateTheGovDotNotifyCheckStatusEndpointForTemporaryFailure() throws Exception {
        final UUID cppNotificationId = randomUUID();
        final UUID govNotifyNotificationId = randomUUID();
        stubGovNotifyCheckStatusWhenTemporaryFailure(cppNotificationId, govNotifyNotificationId);

        final NotificationClient client = new NotificationClient(API_KEY, BASE_URI);

        final Notification response = client.getNotificationById(govNotifyNotificationId.toString());
        assertThat(govNotifyNotificationId, is(response.getId()));
        assertThat(response.getStatus(), is("temporary-failure"));
    }


    @Test
    public void shouldEmulateTheGovDotNotifyCheckStatusEndpointForSuccessAfterTemporaryFailure() throws Exception {
        final UUID cppNotificationId = randomUUID();
        final UUID govNotifyNotificationId = randomUUID();

        stubGovNotifyToReturnTemporaryFailureWhenCheckingStatus(cppNotificationId, govNotifyNotificationId);
        stubGovNotifyToReturnCreatedAsStatusWhenCheckingStatus(cppNotificationId, govNotifyNotificationId);

        final NotificationClient client = new NotificationClient(API_KEY, BASE_URI);

        final Notification response1 = client.getNotificationById(govNotifyNotificationId.toString());
        assertThat(govNotifyNotificationId, is(response1.getId()));
        assertThat(response1.getStatus(), is("temporary-failure"));

        final Notification response2 = client.getNotificationById(govNotifyNotificationId.toString());
        assertThat(govNotifyNotificationId, is(response2.getId()));
        assertThat(response2.getStatus(), is("created"));
    }

    @Test
    public void shouldEmulateTheGovDotNotifyCheckStatusEndpointForPermanentFailureAfterTemporaryFailure() throws Exception {
        final UUID cppNotificationId = randomUUID();
        final UUID govNotifyNotificationId = randomUUID();

        stubGovNotifyToReturnTemporaryFailureWhenCheckingStatus(cppNotificationId, govNotifyNotificationId);
        stubGovNotifyToReturnPermanentFailureAsStatusWhenCheckingStatus(cppNotificationId, govNotifyNotificationId);

        final NotificationClient client = new NotificationClient(API_KEY, BASE_URI);

        final Notification response1 = client.getNotificationById(govNotifyNotificationId.toString());
        assertThat(govNotifyNotificationId, is(response1.getId()));
        assertThat(response1.getStatus(), is("temporary-failure"));

        final Notification response2 = client.getNotificationById(govNotifyNotificationId.toString());

        assertThat(govNotifyNotificationId, is(response2.getId()));
        assertThat(response2.getStatus(), is("permanent-failure"));
    }

    @Test
    public void shouldEmulateTheGovDotNotifyEndpointSendEmail() throws Exception {

        final String personalizationName_1 = "name";
        final String personalizationValue_1 = "Allan";

        final String personalizationName_2 = "amount";
        final String personalizationValue_2 = "10,000,000";

        final String emailSubject = "Email Subject";
        final String emailBody = "Email Body";

        final int templateVersion = 1;

        sendEmailStub()
                .withReference(REFERENCE)
                .withEmailAddress(EMAIL_ADDRESS)
                .withTemplateId(TEMPLATE_ID)
                .withReplyToAddressId(REPLY_TO_ADDRESS_ID)
                .withPersonalization(personalizationName_1, personalizationValue_1)
                .withPersonalization(personalizationName_2, personalizationValue_2)
                .returning(aSuccessfulResponse()
                        .withReference(REFERENCE)
                        .withTemplateId(TEMPLATE_ID)
                        .withReplyToAddressId(of(REPLY_TO_ADDRESS_ID))
                        .withNotificationId(NOTIFICATION_ID)
                        .withTemplateVersion(templateVersion)
                        .withEmailSubject(emailSubject)
                        .withEmailBody(emailBody)
                )
                .build();


        final NotificationClient client = new NotificationClient(API_KEY, BASE_URI);

        final Map<String, String> personalisation = new HashMap<>();
        personalisation.put(personalizationName_1, personalizationValue_1);
        personalisation.put(personalizationName_2, personalizationValue_2);

        final SendEmailResponse response = client.sendEmail(
                TEMPLATE_ID.toString(),
                EMAIL_ADDRESS,
                personalisation,
                REFERENCE.toString(),
                REPLY_TO_ADDRESS_ID.toString());

        final String templateUri = "https://api.notifications.service.gov.uk/services/" + USER_ID + "/templates/" + TEMPLATE_ID;

        assertThat(response.getReference(), is(of(REFERENCE.toString())));
        assertThat(response.getTemplateId(), is(TEMPLATE_ID));
        assertThat(response.getNotificationId(), is(NOTIFICATION_ID));
        assertThat(response.getFromEmail(), is(of(REPLY_TO_ADDRESS_ID.toString())));
        assertThat(response.getTemplateUri(), is(templateUri));
        assertThat(response.getTemplateVersion(), is(templateVersion));
        assertThat(response.getSubject(), is(emailSubject));
        assertThat(response.getBody(), is(emailBody));
    }

    @Test
    public void shouldEmulateTheGovDotNotifyEndpointSendLetter() throws Exception {
        final UUID cppNotificationId = randomUUID();
        final UUID govNotifyNotificationId = randomUUID();
        final String filePath = "pdf/JohnBloggs.pdf";

        stubDocumentDownload(getFileContent(filePath));

        stubGovNotifySendLetterWithStatusDelivered(cppNotificationId, govNotifyNotificationId, getBase64EncodedFileContent(filePath));

        final FileInputStream fileInputStream = new FileInputStream(getFileFrom(filePath));

        final NotificationClient client = new NotificationClient(API_KEY, BASE_URI);

        final LetterResponse response = client.sendPrecompiledLetterWithInputStream(cppNotificationId.toString(), fileInputStream, null);

        assertThat(response.getReference(), is(of(cppNotificationId.toString())));
        assertThat(response.getNotificationId(), is(govNotifyNotificationId));
    }

}
