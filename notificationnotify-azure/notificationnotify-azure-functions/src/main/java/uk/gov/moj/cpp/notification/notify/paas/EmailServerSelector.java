package uk.gov.moj.cpp.notification.notify.paas;

public interface EmailServerSelector {

    public EmailServerSelector setOutlookSettings(final OutlookSettings outlookSettings);

    public OutlookCredentials selectServer();
}
