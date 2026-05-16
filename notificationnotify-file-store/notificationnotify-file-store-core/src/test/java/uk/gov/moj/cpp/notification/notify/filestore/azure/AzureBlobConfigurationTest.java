package uk.gov.moj.cpp.notification.notify.filestore.azure;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AzureBlobConfigurationTest {

    @InjectMocks
    private AzureBlobConfiguration azureBlobConfiguration;

    @Test
    public void shouldReturnConnectionString() {
        setField(azureBlobConfiguration, "connectionString", "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1");

        assertThat(azureBlobConfiguration.getConnectionString(), is("DefaultEndpointsProtocol=http;AccountName=devstoreaccount1"));
    }

    @Test
    public void shouldReturnEndpoint() {
        setField(azureBlobConfiguration, "endpoint", "https://mystorage.blob.core.windows.net");

        assertThat(azureBlobConfiguration.getEndpoint(), is("https://mystorage.blob.core.windows.net"));
    }

    @Test
    public void shouldReturnContainerName() {
        setField(azureBlobConfiguration, "containerName", "notificationnotify-files");

        assertThat(azureBlobConfiguration.getContainerName(), is("notificationnotify-files"));
    }
}
