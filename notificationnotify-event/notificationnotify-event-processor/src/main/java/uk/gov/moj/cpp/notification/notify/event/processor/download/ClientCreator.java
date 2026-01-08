package uk.gov.moj.cpp.notification.notify.event.processor.download;

import static javax.ws.rs.client.ClientBuilder.newClient;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.client.Client;

@ApplicationScoped
public class ClientCreator {

    public Client createNewClient() {
        return newClient();
    }
}
