package uk.gov.moj.cpp.notification.notify.event.processor.download;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.ws.rs.client.Client;

import org.junit.jupiter.api.Test;

public class JaxRsClientProviderTest {

    @Test
    public void shouldReturnClient() {
        final Client actualClient = new JaxRsClientProvider().getClient();

        assertThat(actualClient, is(instanceOf(Client.class)));
    }

    @Test
    public void shouldReuseTheSameClientInstance() {
        final JaxRsClientProvider jaxRsClientProvider = new JaxRsClientProvider();

        assertThat(jaxRsClientProvider.getClient(), is(sameInstance(jaxRsClientProvider.getClient())));
    }

    @Test
    public void shouldCloseTheClientOnPreDestroy() {
        final JaxRsClientProvider jaxRsClientProvider = new JaxRsClientProvider();
        final Client client = jaxRsClientProvider.getClient();

        jaxRsClientProvider.close();

        // The client is closed; invoking it again must fail (JAX-RS mandates IllegalStateException
        // once a Client is closed).
        try {
            client.target("http://localhost");
            fail("Expected the closed client to reject further use");
        } catch (final IllegalStateException expected) {
            // expected once the client is closed
        }
    }
}
