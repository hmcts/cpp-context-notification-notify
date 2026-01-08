package uk.gov.moj.cpp.notification.notify.event.processor.util;

import static java.util.Collections.emptyMap;
import static java.util.Optional.empty;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import uk.gov.justice.json.schemas.domains.notificationnotify.Personalisation;
import uk.gov.moj.cpp.notification.notify.event.processor.download.SuccessfulDocumentDownload;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.SendEmailDetails;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PersonalisationExtractorTest {

    @Mock
    NotificationClient notificationClient;

    @InjectMocks
    private PersonalisationExtractor personalisationExtractor;

    @Test
    public void shouldReturnEmptyPersonalisationMap(){

        final SendEmailDetails sendEmailDetails = mock(SendEmailDetails.class);

        final Optional<Personalisation> personalisation = empty();

        when(sendEmailDetails.getPersonalisation()).thenReturn(personalisation);

        final Map<String ,Object> personalisationMap = personalisationExtractor.extractFrom(sendEmailDetails);
        assertThat(personalisationMap , is(emptyMap()));
    }

    @Test
    public void shouldReturnNotEmptyPersonalisationMap(){
        final Optional<Personalisation> personalisation = Optional.of(new Personalisation(new HashMap<>()));
        personalisation.get().setAdditionalProperty("name", "value");

        final SendEmailDetails sendEmailDetails = mock(SendEmailDetails.class);

        when(sendEmailDetails.getPersonalisation()).thenReturn(personalisation);

        final Map<String ,Object> personalisationMap = personalisationExtractor.extractFrom(sendEmailDetails);

        assertThat(personalisationMap.isEmpty() , is(false));
        assertThat(personalisationMap.get("name"), is("value"));
    }

    @Test
    public void shouldReturnNotEmptyPersonalisationMapForMaterialLink() throws IOException, NotificationClientException {
        final Optional<Personalisation> personalisation = Optional.of(new Personalisation(new HashMap<>()));
        personalisation.get().setAdditionalProperty("name", "value");
        final SuccessfulDocumentDownload successfulDocumentDownload = new SuccessfulDocumentDownload(
                0, new ByteArrayInputStream("fileContents".getBytes()), 199, "content".getBytes(),
                "samplefile.pdf");

        final SendEmailDetails sendEmailDetails = mock(SendEmailDetails.class);
        when(sendEmailDetails.getPersonalisation()).thenReturn(personalisation);

        final Map<String, Object> personalisationMap = personalisationExtractor.extractFrom(sendEmailDetails, successfulDocumentDownload);
        assertThat(personalisationMap.isEmpty(), is(false));
        assertThat(personalisationMap.get("name"), is("value"));
        assertThat(personalisationMap.get("material_url"), is(notNullValue()));
        final JSONObject jsonFileObject = (JSONObject) personalisationMap.get("material_url");
        assertThat(jsonFileObject.get("is_csv"), is(false));
        assertThat(jsonFileObject.get("file"), is(notNullValue()));
    }

    @Test
    public void shouldReturnPersonalisationMapWithMaterialUrlForCsvContents() throws IOException, NotificationClientException {
        final Optional<Personalisation> personalisation = Optional.of(new Personalisation(new HashMap<>()));
        personalisation.get().setAdditionalProperty("name", "value");
        final SuccessfulDocumentDownload successfulDocumentDownload = new SuccessfulDocumentDownload(
                0, new ByteArrayInputStream("csvFileContents".getBytes()), 100,
                "content".getBytes(), "samplefile.csv");

        final SendEmailDetails sendEmailDetails = mock(SendEmailDetails.class);
        when(sendEmailDetails.getPersonalisation()).thenReturn(personalisation);

        final Map<String, Object> personalisationMap = personalisationExtractor.extractFrom(sendEmailDetails, successfulDocumentDownload);
        assertThat(personalisationMap.isEmpty(), is(false));
        assertThat(personalisationMap.get("name"), is("value"));
        assertThat(personalisationMap.get("material_url"), is(notNullValue()));
        final JSONObject jsonFileObject = (JSONObject) personalisationMap.get("material_url");
        assertThat(jsonFileObject.get("is_csv"), is(true));
        assertThat(jsonFileObject.get("file"), is(notNullValue()));
    }

}
