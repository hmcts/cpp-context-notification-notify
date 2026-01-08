package uk.gov.moj.notification.notify.it.stub;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static java.lang.String.format;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.ws.rs.core.HttpHeaders.ACCEPT;
import static javax.ws.rs.core.HttpHeaders.CONTENT_LOCATION;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.HttpHeaders.LOCATION;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static uk.gov.justice.service.wiremock.testutil.InternalEndpointMockUtils.stubPingFor;
import static uk.gov.justice.services.common.http.HeaderConstants.ID;
import static uk.gov.moj.notification.notify.it.stub.CheckStatusResponseBuilder.aNotificationResponse;
import static uk.gov.moj.notification.notify.it.stub.CheckStatusStub.stubGovNotifyCheckStatus;
import static uk.gov.moj.notification.notify.it.stub.CheckStatusStubMultipleResponse.stubGovNotifyCheckStatusForMultipleResponse;
import static uk.gov.moj.notification.notify.it.stub.ErrorResponseBuilder.anErrorResponse;
import static uk.gov.moj.notification.notify.it.stub.SendEmailStub.sendEmailStub;
import static uk.gov.moj.notification.notify.it.stub.SendEmailThroughOffice365Stub.sendEmailThroughOffice365Stub;
import static uk.gov.moj.notification.notify.it.stub.SendLetterResponseBuilder.sendLetterNotificationResponse;
import static uk.gov.moj.notification.notify.it.stub.SendLetterStub.stubSendLetter;
import static uk.gov.moj.notification.notify.it.stub.SuccessfulGovNotifyResponseBuilder.aSuccessfulResponse;
import static uk.gov.moj.notification.notify.it.stub.SuccessfulOffice365NotifyResponseBuilder.successfulOffice365NotifyResponseBuilder;
import static uk.gov.moj.notification.notify.it.util.ResourceLoader.getJsonResponse;
import static uk.gov.moj.notification.notify.it.util.WiremockTestHelper.waitForStubToBeReady;

import uk.gov.justice.service.wiremock.testutil.InternalEndpointMockUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import javax.json.Json;
import javax.json.JsonObject;

import org.apache.commons.codec.binary.Base64;

public class NotificationStubUtils {

    private static final String EMAIL_DOCUMENT_FAILURE_DOWNLOAD_URL_PATH = "http://localhost:999/material-command-api/";

    private static final String DOCUMENT_DOWNLOAD_URL_PATH = "/somewhere/letter/90fe9fe3-0e03-40c3-8298-ec6f55323582";
    public static final String DOCUMENT_DOWNLOAD_URL = "http://localhost:8080" + DOCUMENT_DOWNLOAD_URL_PATH;
    public static final String CLIENT_CONTEXT = "correspondence";

    private static final String USERSGROUPS_USER_URL = "/usersgroups-service/query/api/rest/usersgroups/users/.*";
    private static final String DEFAULT_JSON_CONTENT_TYPE = "application/json";

    private final static UUID TEMPLATE_ID = fromString("e3bc6daf-1d9c-401c-beea-ec155212bd68");
    private final static String EMAIL_ADDRESS = "fred.bloggs@acme.com";
    private final static UUID REPLY_TO_EMAIL_ADDRESS_ID = fromString("9f476d4f-4069-444a-a511-905dcbb66910");

    private final static String personalizationName_1 = "name";
    private final static String personalizationValue_1 = "Allan";
    private final static String personalizationName_2 = "amount";
    private final static String personalizationValue_2 = "10,000,000";
    private final static String personalizationName_3 = "material_url";

    private final static String emailSubject = "Email Subject";
    private final static String emailBody = "Email Body";

    private static final String MATERIAL_QUERY_URL = "/material-service/query/api/rest/material/material/";

    public static void stubGovNotifySuccessClientWithPersonalisationWithoutMaterialUrl(final UUID cppNotificationId,
                                                                                       final UUID govNotifyNotificationId) {
        sendEmailStub()
                .withReference(cppNotificationId)
                .withEmailAddress(EMAIL_ADDRESS)
                .withTemplateId(TEMPLATE_ID)
                .withReplyToAddressId(REPLY_TO_EMAIL_ADDRESS_ID)
                .withPersonalization(personalizationName_1, personalizationValue_1)
                .withPersonalization(personalizationName_2, personalizationValue_2)
                .returning(aSuccessfulResponse()
                        .withNotificationId(govNotifyNotificationId)
                        .withReference(cppNotificationId)
                        .withEmailSubject(emailSubject)
                        .withEmailBody(emailBody)
                )
                .build();
    }

    public static void stubGovNotifySuccessClientWithPersonalisation(final UUID cppNotificationId,
                                                                     final UUID govNotifyNotificationId,
                                                                     final String attachmentContent,
                                                                     final Boolean isCSV) {

        final JsonObject attachmentPersonalisation = Json.createObjectBuilder().add("file", convertToBase64String(attachmentContent)).add("is_csv", isCSV).build();

        sendEmailStub()
                .withReference(cppNotificationId)
                .withEmailAddress(EMAIL_ADDRESS)
                .withTemplateId(TEMPLATE_ID)
                .withReplyToAddressId(REPLY_TO_EMAIL_ADDRESS_ID)
                .withPersonalization(personalizationName_1, personalizationValue_1)
                .withPersonalization(personalizationName_2, personalizationValue_2)
                .withPersonalization(personalizationName_3, attachmentPersonalisation)
                .returning(aSuccessfulResponse()
                        .withNotificationId(govNotifyNotificationId)
                        .withReference(cppNotificationId)
                        .withEmailSubject(emailSubject)
                        .withEmailBody(emailBody)
                )
                .build();
    }

    public static void stubGovNotifySuccessClientWithPersonalisationContainingCsvFile(final UUID cppNotificationId,
                                                                     final UUID govNotifyNotificationId,
                                                                     final String attachmentContent,
                                                                     final Boolean isCSV) {

        final JsonObject attachmentPersonalisation = Json.createObjectBuilder()
                .add("file", attachmentContent).add("is_csv", isCSV).build();

        sendEmailStub()
                .withReference(cppNotificationId)
                .withEmailAddress(EMAIL_ADDRESS)
                .withTemplateId(TEMPLATE_ID)
                .withReplyToAddressId(REPLY_TO_EMAIL_ADDRESS_ID)
                .withPersonalization(personalizationName_3, attachmentPersonalisation)
                .returning(aSuccessfulResponse()
                        .withNotificationId(govNotifyNotificationId)
                        .withReference(cppNotificationId)
                        .withEmailSubject(emailSubject)
                        .withEmailBody(emailBody)
                )
                .build();
    }

    private static String convertToBase64String(final String attachmentContent) {
        final byte[] base64 = Base64.encodeBase64(attachmentContent.getBytes());
        return new String(base64, StandardCharsets.ISO_8859_1);
    }

    public static void stubGovNotifySuccessClientWithoutPersonalisation(final UUID cppNotificationId,
                                                                        final UUID govNotifyNotificationId) {
        sendEmailStub()
                .withReference(cppNotificationId)
                .withEmailAddress(EMAIL_ADDRESS)
                .withTemplateId(TEMPLATE_ID)
                .returning(aSuccessfulResponse()
                        .withNotificationId(govNotifyNotificationId)
                        .withReference(cppNotificationId)
                        .withEmailSubject(emailSubject)
                        .withEmailBody(emailBody)
                )
                .build();
    }

    public static void stubOffice365NotifySuccessClientWithPersonalisation(final UUID cppNotificationId,
                                                                           final String materialUrl) {
        sendEmailThroughOffice365Stub()
                .withReference(cppNotificationId)
                .withEmailAddress(EMAIL_ADDRESS)
                .withTemplateId(TEMPLATE_ID)
                .withReplyToAddressId(REPLY_TO_EMAIL_ADDRESS_ID)
                .withPersonalization(personalizationName_1, personalizationValue_1)
                .withPersonalization(personalizationName_2, personalizationValue_2)
                .withPersonalization(personalizationName_3, materialUrl)
                .returning(successfulOffice365NotifyResponseBuilder()
                        .withNotificationId(cppNotificationId)
                )
                .build();
    }


    public static void stubGovNotifyPermanentFailureForSendEmailWithMaterialLink(final UUID cppNotificationId,
                                                                                 final String materialLink) {
        sendEmailStub()
                .withReference(cppNotificationId)
                .withEmailAddress(EMAIL_ADDRESS)
                .withTemplateId(TEMPLATE_ID)
                .withReplyToAddressId(REPLY_TO_EMAIL_ADDRESS_ID)
                .withPersonalization(personalizationName_1, personalizationValue_1)
                .withPersonalization(personalizationName_2, personalizationValue_2)
                .withPersonalization(personalizationName_3, materialLink)

                .returning(anErrorResponse()
                        .withError("BadRequestError")
                        .withMessage("Template not found")
                        .withStatus(BAD_REQUEST)
                )
                .build();
    }

    public static void stubGovNotifyPermanentFailureForSendEmail(final UUID cppNotificationId) {
        sendEmailStub()
                .withReference(cppNotificationId)
                .withEmailAddress(EMAIL_ADDRESS)
                .withTemplateId(TEMPLATE_ID)
                .withReplyToAddressId(REPLY_TO_EMAIL_ADDRESS_ID)
                .withPersonalization(personalizationName_1, personalizationValue_1)
                .withPersonalization(personalizationName_2, personalizationValue_2)
                .returning(anErrorResponse()
                        .withError("BadRequestError")
                        .withMessage("Template not found")
                        .withStatus(BAD_REQUEST)
                )
                .build();
    }

    public static void stubGovNotifyTemporaryFailureForSendEmail(final UUID cppNotificationId) {
        sendEmailStub()
                .withReference(cppNotificationId)
                .withEmailAddress(EMAIL_ADDRESS)
                .withTemplateId(TEMPLATE_ID)
                .withReplyToAddressId(REPLY_TO_EMAIL_ADDRESS_ID)
                .withPersonalization(personalizationName_1, personalizationValue_1)
                .withPersonalization(personalizationName_2, personalizationValue_2)
                .returning(anErrorResponse()
                        .withError("Internal Server Error")
                        .withMessage("Internal Server Error")
                        .withStatus(INTERNAL_SERVER_ERROR)
                )
                .build();
    }

    public static void stubGovNotifyCheckStatusWithStatusDelivered(
            final UUID cppNotificationId,
            final UUID govNotifyNotificationId) {
        stubGovNotifyCheckStatus()
                .withUrlPathContaining(govNotifyNotificationId.toString())
                .returning(aNotificationResponse(cppNotificationId, govNotifyNotificationId)
                        .withStatus("delivered"))
                .build();
    }


    public static void stubGovNotifyCheckLetterStatusWithStatusReceived(
            final UUID cppNotificationId,
            final UUID govNotifyNotificationId) {
        stubGovNotifyCheckStatus()
                .withUrlPathContaining(govNotifyNotificationId.toString())
                .returning(aNotificationResponse(cppNotificationId, govNotifyNotificationId)
                        .withStatus("received"))
                .build();
    }

    public static void stubGovNotifyCheckStatusWithStatusNotFound(
            final UUID cppNotificationId,
            final UUID govNotifyNotificationId) {
        stubGovNotifyCheckStatus()
                .withUrlPathContaining(govNotifyNotificationId.toString())
                .returning(aNotificationResponse(cppNotificationId, govNotifyNotificationId)
                        .withStatus("not found"))
                .build();
    }

    public static void stubGovNotifyCheckStatusWithStatusValidationError(
            final UUID cppNotificationId,
            final UUID govNotifyNotificationId) {
        stubGovNotifyCheckStatus()
                .withUrlPathContaining(govNotifyNotificationId.toString())
                .returning(aNotificationResponse(cppNotificationId, govNotifyNotificationId)
                        .withStatus("validation-failed"))
                .build();
    }

    public static void stubGovNotifyCheckStatusWithStatusUnexpected(
            final UUID cppNotificationId,
            final UUID govNotifyNotificationId) {
        stubGovNotifyCheckStatus()
                .withUrlPathContaining(govNotifyNotificationId.toString())
                .returning(aNotificationResponse(cppNotificationId, govNotifyNotificationId)
                        .withStatus("unexpected"))
                .build();
    }

    public static void stubGovNotifyCheckStatusWhenPermanentFailure(
            final UUID cppNotificationId,
            final UUID govNotifyNotificationId) {
        stubGovNotifyCheckStatus()
                .withUrlPathContaining(govNotifyNotificationId.toString())
                .returning(aNotificationResponse(cppNotificationId, govNotifyNotificationId)
                        .withStatus("permanent-failure"))
                .build();
    }


    public static void stubGovNotifyCheckStatusWhenTemporaryFailure(
            final UUID cppNotificationId,
            final UUID govNotifyNotificationId) {
        stubGovNotifyCheckStatus()
                .withUrlPathContaining(govNotifyNotificationId.toString())
                .returning(aNotificationResponse(cppNotificationId, govNotifyNotificationId)
                        .withStatus("temporary-failure"))
                .build();
    }

    public static void stubGovNotifyToReturnTemporaryFailureWhenCheckingStatus(
            final UUID cppNotificationId,
            final UUID govNotifyNotificationId) {

        stubGovNotifyCheckStatusForMultipleResponse()
                .withScenario("check for created status")
                .withScenarioState(STARTED)
                .withNextScenarioState("failed")
                .withUrlPathContaining(govNotifyNotificationId.toString())
                .returning(aNotificationResponse(cppNotificationId, govNotifyNotificationId).withStatus("temporary-failure")).build();
    }

    public static void stubGovNotifyToReturnPermanentFailureAsStatusWhenCheckingStatus(
            final UUID cppNotificationId,
            final UUID govNotifyNotificationId) {

        stubGovNotifyCheckStatusForMultipleResponse()
                .withScenario("check for created status")
                .withScenarioState("failed")
                .withNextScenarioState("permanent-failure")
                .withUrlPathContaining(govNotifyNotificationId.toString())
                .returning(aNotificationResponse(cppNotificationId, govNotifyNotificationId).withStatus("permanent-failure")).build();

    }

    public static void stubGovNotifyToReturnCreatedAsStatusWhenCheckingStatus(
            final UUID cppNotificationId,
            final UUID govNotifyNotificationId) {

        stubGovNotifyCheckStatusForMultipleResponse()
                .withUrlPathContaining(govNotifyNotificationId.toString())
                .withScenario("check for created status")
                .withScenarioState("failed")
                .withNextScenarioState("created")
                .returning(aNotificationResponse(cppNotificationId, govNotifyNotificationId).withStatus("created")).build();

    }

    public static void stubGovNotifySendLetterWithStatusDelivered(final UUID cppNotificationId,
                                                                  final UUID govNotifyNotificationId,
                                                                  final String content)
            throws IOException {
        stubSendLetter()
                .withReference(cppNotificationId)
                .withContent(content)
                .returning(sendLetterNotificationResponse(cppNotificationId, govNotifyNotificationId)
                        .withNotificationId(govNotifyNotificationId)
                        .withReference(cppNotificationId)

                )
                .build();
    }

    public static void stubGovNotifySendLetterWithStatusDeliveredWithFirstClassPostage(final UUID cppNotificationId,
                                                                                       final UUID govNotifyNotificationId,
                                                                                       final String content)
            throws IOException {
        stubSendLetter()
                .withReference(cppNotificationId)
                .withContent(content)
                .withPostage("first")
                .returning(sendLetterNotificationResponse(cppNotificationId, govNotifyNotificationId)
                        .withNotificationId(govNotifyNotificationId)
                        .withReference(cppNotificationId)

                )
                .build();
    }

    public static void stubGovNotifyPermanentFailureForSendLetter(final UUID cppNotificationId,
                                                                  final String inputStream)
            throws IOException {
        stubSendLetter()
                .withReference(cppNotificationId)
                .withContent(inputStream)
                .returning(anErrorResponse()
                        .withError("BadRequestError")
                        .withMessage("precompiledPDF must be a valid PDF file1")
                        .withStatus(BAD_REQUEST)
                )
                .build();
    }

    public static void stubGovNotifyPermanentFailureForVirusScannedFailed(final UUID cppNotificationId,
                                                                          final String content) throws IOException {
        stubSendLetter()
                .withReference(cppNotificationId)
                .withContent(content)
                .returning(anErrorResponse()
                        .withError("virus-scan-failed")
                        .withMessage("virus-scan-failed")
                        .withStatus(BAD_REQUEST)
                )
                .build();
    }

    public static void stubGovNotifyForPendingVirusCheck(final UUID cppNotificationId, final UUID govNotifyNotificationId) {
        stubGovNotifyCheckStatus()
                .withUrlPathContaining(govNotifyNotificationId.toString())
                .returning(aNotificationResponse(cppNotificationId, govNotifyNotificationId)
                        .withStatus("pending-virus-check"))
                .build();
    }

    public static void stubGovNotifyForLetterAccepted(final UUID cppNotificationId, final UUID govNotifyNotificationId) {
        stubGovNotifyCheckStatus()
                .withUrlPathContaining(govNotifyNotificationId.toString())
                .returning(aNotificationResponse(cppNotificationId, govNotifyNotificationId)
                        .withStatus("accepted"))
                .build();
    }

    public static void stubGovNotifyTemporaryFailureForSendLetter(final UUID cppNotificationId,
                                                                  final String inputStream) throws IOException {
        stubSendLetter()
                .withReference(cppNotificationId)
                .withContent(inputStream)
                .returning(anErrorResponse()
                        .withError("Internal Server Error")
                        .withMessage("Internal Server Error")
                        .withStatus(INTERNAL_SERVER_ERROR)
                )
                .build();
    }

    public static void stubUsersGroups() throws IOException {
        stubPingFor("usersgroups-service");
        stubFor(get(urlPathMatching(USERSGROUPS_USER_URL))
                .willReturn(aResponse()
                        .withStatus(SC_OK)
                        .withHeader(ID, randomUUID().toString())
                        .withBody(getJsonResponse("stub-data/usersgroups.query.usergroups.json"))));
    }

    public static void stubEmailDocumentDownloadFailure() {
        stubFor(get(urlPathMatching(EMAIL_DOCUMENT_FAILURE_DOWNLOAD_URL_PATH))
                .willReturn(aResponse()
                        .withStatus(SC_INTERNAL_SERVER_ERROR)
                        .withHeader(ID, UUID.randomUUID().toString())
                        .withHeader(CONTENT_TYPE, APPLICATION_OCTET_STREAM)
                        .withBody("")));
    }

    public static void stubDocumentDownloadFailure() {
        stubFor(get(urlPathMatching(DOCUMENT_DOWNLOAD_URL_PATH))
                .willReturn(aResponse()
                        .withStatus(SC_INTERNAL_SERVER_ERROR)
                        .withHeader(ID, UUID.randomUUID().toString())
                        .withHeader(CONTENT_TYPE, APPLICATION_OCTET_STREAM)
                        .withBody("")));
    }

    public static void stubDocumentDownload(final byte[] content) {
        stubFor(get(urlPathMatching(DOCUMENT_DOWNLOAD_URL_PATH))
                .willReturn(aResponse()
                        .withStatus(SC_OK)
                        .withHeader(ID, randomUUID().toString())
                        .withHeader(CONTENT_TYPE, APPLICATION_OCTET_STREAM)
                        .withHeader(CONTENT_LOCATION, "filename=sampleFile.pdf")
                        .withBody(content)));
    }

    public static void stubMaterialContent(final UUID materialId, final byte[] materialContent) {
        stubFor(get(urlPathMatching(MATERIAL_QUERY_URL + materialId.toString()))
                .withQueryParam("stream", equalTo("true"))
                .withQueryParam("requestPdf", equalTo("true"))
                .withHeader(ACCEPT, equalTo("application/vnd.material.query.material+json"))
                .willReturn(aResponse()
                        .withStatus(SC_OK)
                        .withHeader(ID, randomUUID().toString())
                        .withHeader(CONTENT_TYPE, "text/uri-list")
                        .withHeader(CONTENT_LOCATION, "filename=sampleFile.pdf")
                        .withHeader(LOCATION, "http://localhost:8080/azureside/"+materialId.toString())
                ));

        stubFor(get(urlPathMatching("/azureside/"+materialId.toString()))
                .willReturn(aResponse()
                        .withStatus(SC_OK)
                        .withHeader(ID, randomUUID().toString())
                        .withHeader(CONTENT_TYPE, "application/pdf")
                        .withHeader(CONTENT_LOCATION, "filename=sampleFile.pdf")
                        .withBody(materialContent)));
    }

    public static void stubEnableAllCapabilities() {
        String stubUrl = format("/authorisation-service-server/rest/capabilities/%s", ".*");
        String responsePayload = Json.createObjectBuilder().add("enabled", true).build().toString();
        InternalEndpointMockUtils.stubPingFor("authorisation-service-server");

        stubFor(get(urlMatching(stubUrl))
                .willReturn(aResponse().withStatus(SC_OK)
                        .withHeader(ID, randomUUID().toString())
                        .withHeader("Content-Type", DEFAULT_JSON_CONTENT_TYPE)
                        .withBody(responsePayload)));

        waitForStubToBeReady(stubUrl, "application/vnd.authorisation.capability+json");
    }
}

