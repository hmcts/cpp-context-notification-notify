package uk.gov.moj.notification.notify.it;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.gov.justice.services.integrationtest.utils.jms.JmsResourceManagementExtension;

import java.util.concurrent.atomic.AtomicBoolean;

@ExtendWith(JmsResourceManagementExtension.class)
public class BaseIT {

    public static final String CONTEXT_NAME = "notificationnotify";
    private static AtomicBoolean atomicBoolean = new AtomicBoolean();

    @BeforeAll
    public static void setupOnce() throws Throwable {
        if (!atomicBoolean.get()) {
            atomicBoolean.set(true);

            WireMock.configureFor(System.getProperty("INTEGRATION_HOST_KEY", "localhost"), 8080);
            WireMock.reset();
        }
    }

}
