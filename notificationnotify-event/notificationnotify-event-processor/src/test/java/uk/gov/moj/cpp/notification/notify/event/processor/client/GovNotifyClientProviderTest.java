package uk.gov.moj.cpp.notification.notify.event.processor.client;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;

import uk.gov.service.notify.NotificationClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class GovNotifyClientProviderTest {

    @Mock
    private NotificationClient notificationClient;

    @InjectMocks
    private GovNotifyClientProvider govNotifyClientProvider;

    @BeforeEach
    public void setup() {
        setField(govNotifyClientProvider, "apiKey", "key");
        setField(govNotifyClientProvider, "govNotifyHostUrl", "http://localhost:8080");
        setField(govNotifyClientProvider, "proxyEnabled", "false");
        setField(govNotifyClientProvider, "notificationClient", notificationClient);
    }

    @Test
    public void shouldReturnNotificationClient() {
        final NotificationClient client = govNotifyClientProvider.getClient();
        assertThat(client, is(notificationClient));
    }
}
