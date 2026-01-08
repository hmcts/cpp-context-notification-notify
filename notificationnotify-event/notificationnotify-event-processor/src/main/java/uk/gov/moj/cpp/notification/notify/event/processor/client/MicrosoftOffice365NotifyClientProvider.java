package uk.gov.moj.cpp.notification.notify.event.processor.client;

import uk.gov.justice.services.common.configuration.GlobalValue;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class MicrosoftOffice365NotifyClientProvider {

    @Inject
    @GlobalValue(key = "notify.email.apim.endpoint")
    private String office365NotifyApiEndpoint;

    @Inject
    @GlobalValue(key = "notify.email.apim.subscription.key")
    private String subscriptionKey;

    public String getOffice365NotifyApiEndpoint() {
        return office365NotifyApiEndpoint;
    }

    public String getSubscriptionKey() {
        return subscriptionKey;
    }
}
