package uk.gov.moj.cpp.notification.notify.paas;

public final class OutlookCredentialsBuilder {
    private String server;
    private String port;
    private String username;
    private String password;

    private OutlookCredentialsBuilder() {
    }

    public static OutlookCredentialsBuilder anOutlookCredentials() {
        return new OutlookCredentialsBuilder();
    }

    public OutlookCredentialsBuilder withServer(String server) {
        this.server = server;
        return this;
    }

    public OutlookCredentialsBuilder withPort(String port) {
        this.port = port;
        return this;
    }

    public OutlookCredentialsBuilder withUsername(String username) {
        this.username = username;
        return this;
    }

    public OutlookCredentialsBuilder withPassword(String password) {
        this.password = password;
        return this;
    }

    public OutlookCredentials build() {
       final  OutlookCredentials outlookCredentials = new OutlookCredentials();
        outlookCredentials.setServer(server);
        outlookCredentials.setPort(port);
        outlookCredentials.setUsername(username);
        outlookCredentials.setPassword(password);
        return outlookCredentials;
    }
}
