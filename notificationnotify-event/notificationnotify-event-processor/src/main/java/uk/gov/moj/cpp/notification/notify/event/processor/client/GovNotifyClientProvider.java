package uk.gov.moj.cpp.notification.notify.event.processor.client;

import static java.net.Proxy.Type.HTTP;

import uk.gov.justice.services.common.configuration.GlobalValue;
import uk.gov.service.notify.NotificationClient;

import java.net.InetSocketAddress;
import java.net.Proxy;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class GovNotifyClientProvider {

    @Inject
    @GlobalValue(key = "gov.notify.api.key", defaultValue = "apitestkey-71c15d73-d75a-42d6-bd66-3dfc0c9a3bb5-fe28069f-9543-42f5-bb89-3d870dbab221")
    private String apiKey;

    @Inject
    @GlobalValue(key = "gov.notify.host.url", defaultValue = "http://localhost:8080")
    private String govNotifyHostUrl;

    @Inject
    @GlobalValue(key = "gov.notify.proxy.enabled", defaultValue = "false")
    private String proxyEnabled;

    @Inject
    @GlobalValue(key = "gov.notify.proxy.host", defaultValue = "localhost")
    private String proxyHost;

    @Inject
    @GlobalValue(key = "gov.notify.proxy.port", defaultValue = "8080")
    private String proxyPort;

    private NotificationClient notificationClient;

    public NotificationClient getClient() {
        if (notificationClient == null) {
            final Proxy proxy = proxy();

            notificationClient = new NotificationClient(apiKey, govNotifyHostUrl, proxy);
        }
        return notificationClient;
    }

    private Proxy proxy() {
        Proxy proxy = null;

        if (Boolean.valueOf(proxyEnabled)) {
            proxy = new Proxy(HTTP, new InetSocketAddress(proxyHost, Integer.valueOf(proxyPort)));
        }

        return proxy;
    }
}
