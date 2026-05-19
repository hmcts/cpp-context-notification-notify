package uk.gov.moj.cpp.notification.notify.filestore.azure;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;

import java.time.Duration;

import org.junit.jupiter.api.Test;

public class AzureBlobConfigurationTest {

    @Test
    public void shouldReturnConnectionString() {
        final AzureBlobConfiguration azureBlobConfiguration = new AzureBlobConfiguration();
        setField(azureBlobConfiguration, "connectionString", "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1");

        assertThat(azureBlobConfiguration.getConnectionString(), is("DefaultEndpointsProtocol=http;AccountName=devstoreaccount1"));
    }

    @Test
    public void shouldReturnTrueForHasConnectionStringWhenRealConnectionStringSet() {
        final AzureBlobConfiguration azureBlobConfiguration = new AzureBlobConfiguration();
        setField(azureBlobConfiguration, "connectionString", "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1");

        assertThat(azureBlobConfiguration.hasConnectionString(), is(true));
    }

    @Test
    public void shouldReturnFalseForHasConnectionStringWhenSentinelValue() {
        final AzureBlobConfiguration azureBlobConfiguration = new AzureBlobConfiguration();
        setField(azureBlobConfiguration, "connectionString", "DefaultAzureCredential");

        assertThat(azureBlobConfiguration.hasConnectionString(), is(false));
    }

    @Test
    public void shouldReturnFalseForHasConnectionStringWhenBlank() {
        final AzureBlobConfiguration azureBlobConfiguration = new AzureBlobConfiguration();
        setField(azureBlobConfiguration, "connectionString", "");

        assertThat(azureBlobConfiguration.hasConnectionString(), is(false));
    }

    @Test
    public void shouldReturnEndpoint() {
        final AzureBlobConfiguration azureBlobConfiguration = new AzureBlobConfiguration();
        setField(azureBlobConfiguration, "endpoint", "https://mystorage.blob.core.windows.net");

        assertThat(azureBlobConfiguration.getEndpoint(), is("https://mystorage.blob.core.windows.net"));
    }

    @Test
    public void shouldReturnContainerName() {
        final AzureBlobConfiguration azureBlobConfiguration = new AzureBlobConfiguration();
        setField(azureBlobConfiguration, "containerName", "notificationnotify-files");

        assertThat(azureBlobConfiguration.getContainerName(), is("notificationnotify-files"));
    }

    @Test
    public void shouldReturnConnectionTimeout() {
        final AzureBlobConfiguration azureBlobConfiguration = new AzureBlobConfiguration();
        setField(azureBlobConfiguration, "connectionTimeoutSeconds", "10");

        assertThat(azureBlobConfiguration.getConnectionTimeout(), is(Duration.ofSeconds(10)));
    }

    @Test
    public void shouldReturnResponseTimeout() {
        final AzureBlobConfiguration azureBlobConfiguration = new AzureBlobConfiguration();
        setField(azureBlobConfiguration, "responseTimeoutSeconds", "30");

        assertThat(azureBlobConfiguration.getResponseTimeout(), is(Duration.ofSeconds(30)));
    }

    @Test
    public void shouldReturnTransferTimeoutAsDuration() {
        final AzureBlobConfiguration azureBlobConfiguration = new AzureBlobConfiguration();
        setField(azureBlobConfiguration, "transferTimeoutSeconds", "300");

        assertThat(azureBlobConfiguration.getTransferTimeout(), is(Duration.ofSeconds(300)));
    }
}
