package uk.gov.moj.cpp.notification.notify.paas;

import java.util.Random;

@SuppressWarnings("squid:S2245")
public class RandomServerSelector implements EmailServerSelector {
    private OutlookSettings outlookSettings;
    private int serverCount;
    private static final Random rand = new Random();

    public EmailServerSelector setOutlookSettings(final OutlookSettings outlookSettings) {
        this.outlookSettings = outlookSettings;
        this.serverCount = outlookSettings.getOutlookCredentials().size();
        return this;
    }

    public OutlookCredentials selectServer() {
        return outlookSettings.getOutlookCredentials().get(rand.nextInt(serverCount));
    }
}
