package uk.gov.moj.cpp.notification.notify.filestore.azure;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.core.exception.HttpResponseException;
import com.azure.core.http.HttpResponse;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobStorageException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
public class AzureBlobContainerClientProducerTest {

    // Azurite well-known public development key — not a real secret.
    // See: https://learn.microsoft.com/azure/storage/common/storage-use-azurite
    private static final String AZURITE_CONNECTION_STRING =
            "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;" +
            "AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/" +
            "K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://localhost:10000/devstoreaccount1;";

    @Mock
    private AzureBlobConfiguration azureBlobConfiguration;

    @Mock
    private Logger logger;

    @InjectMocks
    private AzureBlobContainerClientProducer producer;

    @Test
    public void shouldCallCreateIfNotExistsOnInitialise() {
        final BlobContainerClient containerClient = mock(BlobContainerClient.class);
        final AzureBlobContainerClientProducer spiedProducer = spy(producer);
        doReturn(containerClient).when(spiedProducer).buildBlobContainerClient(azureBlobConfiguration);

        spiedProducer.initialise();

        verify(containerClient).createIfNotExists();
    }

    @Test
    public void shouldReturnBuiltContainerClientFromProducerMethod() {
        final BlobContainerClient containerClient = mock(BlobContainerClient.class);
        final AzureBlobContainerClientProducer spiedProducer = spy(producer);
        doReturn(containerClient).when(spiedProducer).buildBlobContainerClient(azureBlobConfiguration);

        spiedProducer.initialise();

        assertThat(spiedProducer.blobContainerClient(), is(containerClient));
    }

    @Test
    public void shouldLogWarningWhenCreateIfNotExistsThrows() {
        final BlobContainerClient containerClient = mock(BlobContainerClient.class);
        final AzureBlobContainerClientProducer spiedProducer = spy(producer);
        doReturn(containerClient).when(spiedProducer).buildBlobContainerClient(azureBlobConfiguration);
        when(azureBlobConfiguration.getContainerName()).thenReturn("notificationnotify-files");
        final BlobStorageException blobStorageException = mock(BlobStorageException.class);
        when(blobStorageException.getMessage()).thenReturn("Container already exists");
        doThrow(blobStorageException).when(containerClient).createIfNotExists();

        spiedProducer.initialise();

        verify(logger).warn(
                "createIfNotExists failed for container '{}' — assuming it already exists: {}",
                "notificationnotify-files",
                "Container already exists");
    }

    @Test
    public void shouldLogWarningWhenCreateIfNotExistsThrowsHttpResponseExceptionWith409() {
        final BlobContainerClient containerClient = mock(BlobContainerClient.class);
        final AzureBlobContainerClientProducer spiedProducer = spy(producer);
        doReturn(containerClient).when(spiedProducer).buildBlobContainerClient(azureBlobConfiguration);
        when(azureBlobConfiguration.getContainerName()).thenReturn("notificationnotify-files");
        final HttpResponseException httpResponseException = mock(HttpResponseException.class);
        final HttpResponse httpResponse = mock(HttpResponse.class);
        when(httpResponseException.getResponse()).thenReturn(httpResponse);
        when(httpResponse.getStatusCode()).thenReturn(409);
        when(httpResponseException.getMessage()).thenReturn("ContainerAlreadyExists (XML response from Azurite)");
        doThrow(httpResponseException).when(containerClient).createIfNotExists();

        spiedProducer.initialise();

        verify(logger).warn(
                "createIfNotExists failed for container '{}' — assuming it already exists: {}",
                "notificationnotify-files",
                "ContainerAlreadyExists (XML response from Azurite)");
    }

    @Test
    public void shouldBuildClientFromConnectionString() {
        when(azureBlobConfiguration.hasConnectionString()).thenReturn(true);
        when(azureBlobConfiguration.getConnectionString()).thenReturn(AZURITE_CONNECTION_STRING);
        when(azureBlobConfiguration.getContainerName()).thenReturn("test-container");

        final BlobContainerClient blobContainerClient = producer.buildBlobContainerClient(azureBlobConfiguration);

        assertThat(blobContainerClient, is(notNullValue()));
        assertThat(blobContainerClient.getBlobContainerName(), is("test-container"));
    }

    @Test
    public void shouldBuildClientUsingDefaultAzureCredentialWhenNoConnectionStringPresent() {
        when(azureBlobConfiguration.getEndpoint()).thenReturn("https://devstoreaccount1.blob.core.windows.net");
        when(azureBlobConfiguration.getContainerName()).thenReturn("test-container");

        final BlobContainerClient blobContainerClient = producer.buildBlobContainerClient(azureBlobConfiguration);

        assertThat(blobContainerClient, is(notNullValue()));
        assertThat(blobContainerClient.getBlobContainerName(), is("test-container"));
    }
}
