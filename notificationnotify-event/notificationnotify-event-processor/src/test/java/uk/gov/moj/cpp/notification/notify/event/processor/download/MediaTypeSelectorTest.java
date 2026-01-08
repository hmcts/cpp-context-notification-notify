package uk.gov.moj.cpp.notification.notify.event.processor.download;

import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MediaTypeSelectorTest {

    @InjectMocks
    private MediaTypeSelector mediaTypeSelector;

    @Test
    public void shouldReturnApplicationOctetStreamMediaTypeForNonMaterialUrl() {
        final String actualMediaType = mediaTypeSelector.mediaTypeFor("http://localhost:8080/other-query-api/other");

        assertThat(actualMediaType, is(APPLICATION_OCTET_STREAM));
    }

    @Test
    public void shouldReturnMaterialMediaTypeForMaterialUrl() {
        final String actualMediaType = mediaTypeSelector.mediaTypeFor("http://localhost:8080/material-query-api/query/api/rest/material/material/3434?stream=true&requestPdf=true");

        assertThat(actualMediaType, is("application/vnd.material.query.material+json"));
    }
}