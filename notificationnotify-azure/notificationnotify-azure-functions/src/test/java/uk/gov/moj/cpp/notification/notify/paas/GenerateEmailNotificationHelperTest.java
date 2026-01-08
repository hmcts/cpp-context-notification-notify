package uk.gov.moj.cpp.notification.notify.paas;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.moj.cpp.notification.notify.paas.GenerateEmailNotificationHelper.getFirstLine;
import static uk.gov.moj.cpp.notification.notify.paas.GenerateEmailNotificationHelper.withoutFirstLine;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.google.common.io.Resources;
import org.junit.jupiter.api.Test;

public class GenerateEmailNotificationHelperTest {

    @Test
    public void testPopulateTemplate() {
        final String template = "My name is ((name)) and I live in ((location)).";

        final Map<String, String> personalisation = new HashMap<>();

        final String name = "Jane";
        final String location = "Croydon";
        personalisation.put("name", name);
        personalisation.put("location", location);

        final String populatedTemplate = GenerateEmailNotificationHelper.populateTemplate(template, personalisation);
        assertThat(populatedTemplate, is(String.format("My name is %s and I live in %s.", name, location)));

    }

    @Test
    public void testWithoutFirstLine() throws IOException {
        final URL url = Resources.getResource("templates/c7482552-ba85-4feb-8e91-771ccf17764a.template");
        final String templateString = Resources.toString(url, StandardCharsets.UTF_8);
        assertThat(templateString.contains("The rest is the body"), is(true));

        final String result = withoutFirstLine(templateString);
        assertThat(result.contains("The rest is the body"), is(false));
        assertThat(result.contains("Auto generated email."), is(true));

    }

    @Test
    public void testGetFirstLine() throws IOException {

        final URL url = Resources.getResource("templates/c7482552-ba85-4feb-8e91-771ccf17764a.template");
        final String templateString = Resources.toString(url, StandardCharsets.UTF_8);
        assertThat(templateString.contains("The rest is the body"), is(true));

        final String result = getFirstLine(withoutFirstLine(templateString));
        assertThat(result, is("CASE URN: ((URN))"));

    }
}