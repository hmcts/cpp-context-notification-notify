package uk.gov.moj.cpp.notification.notify.paas;

public class OutlookCredentials {
    private String server;
    private String port;
    private String username;
    private String password;
    private int serverId;

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getServerId() {
        return serverId;
    }

    public void setServerId(final int serverId) {
        this.serverId = serverId;
    }
}
