package uk.gov.moj.cpp.notification.notify.event.processor.download;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import javax.ws.rs.client.Client;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

public class ClientCreatorTest {

    @Test
    public void shouldReturnClientBuilder() {
        final Client actualClientBuilder = new ClientCreator().createNewClient();

        assertThat(actualClientBuilder, is(CoreMatchers.instanceOf(Client.class)));
    }
}