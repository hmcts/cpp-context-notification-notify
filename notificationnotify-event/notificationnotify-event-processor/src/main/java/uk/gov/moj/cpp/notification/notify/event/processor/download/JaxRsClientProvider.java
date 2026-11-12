package uk.gov.moj.cpp.notification.notify.event.processor.download;

import static jakarta.ws.rs.client.ClientBuilder.newClient;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.client.Client;

@ApplicationScoped
public class JaxRsClientProvider {

    // A JAX-RS Client owns a connection pool and is expensive to create and thread-safe to
    // reuse, so hold a single instance for the lifetime of the bean and close it on shutdown.
    // Creating one per request leaked the pool (the Client backs the returned Response, so it
    // cannot be closed inside the download call).
    private final Client client = newClient();

    public Client getClient() {
        return client;
    }

    @PreDestroy
    void close() {
        client.close();
    }
}
