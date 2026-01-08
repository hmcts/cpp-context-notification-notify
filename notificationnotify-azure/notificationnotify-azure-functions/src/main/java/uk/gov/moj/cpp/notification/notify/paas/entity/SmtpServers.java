package uk.gov.moj.cpp.notification.notify.paas.entity;

import com.microsoft.azure.storage.table.TableServiceEntity;

public class SmtpServers extends TableServiceEntity {

    private String address;
    private String port;
    private String username;
    private String password;
    private long hits;


    public SmtpServers() {
        //defaultConstructor
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(final String address) {
        this.address = address;
    }

    public String getPort() {
        return port;
    }

    public void setPort(final String port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(final String password) {
        this.password = password;
    }

    public long getHits() {
        return hits;
    }

    public void setHits(final long hits) {
        this.hits = hits;
    }


    @Override
    public String toString() {
        return "SmtpServers{" +
                "address='" + address + '\'' +
                ", port='" + port + '\'' +
                ", username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", hits=" + hits +
                ", partitionKey='" + partitionKey + '\'' +
                ", rowKey='" + rowKey + '\'' +
                ", etag='" + etag + '\'' +
                ", timeStamp=" + timeStamp +
                '}';
    }

    public static final class SmtpServersBuilder {
        private String address;
        private String port;
        private String username;
        private String password;
        private long hits;
        private String partitionKey;
        private String rowKey;
        private String etag;

        private SmtpServersBuilder() {
        }

        public static SmtpServersBuilder aSmtpServers() {
            return new SmtpServersBuilder();
        }

        public SmtpServersBuilder withAddress(String address) {
            this.address = address;
            return this;
        }

        public SmtpServersBuilder withPort(String port) {
            this.port = port;
            return this;
        }

        public SmtpServersBuilder withUsername(String username) {
            this.username = username;
            return this;
        }

        public SmtpServersBuilder withPassword(String password) {
            this.password = password;
            return this;
        }

        public SmtpServersBuilder withHits(long hits) {
            this.hits = hits;
            return this;
        }

        public SmtpServersBuilder withPartitionKey(String partitionKey) {
            this.partitionKey = partitionKey;
            return this;
        }

        public SmtpServersBuilder withRowKey(String rowKey) {
            this.rowKey = rowKey;
            return this;
        }

        public SmtpServersBuilder withEtag(String etag) {
            this.etag = etag;
            return this;
        }

        public SmtpServers build() {
            final SmtpServers smtpServers = new SmtpServers();
            smtpServers.setAddress(address);
            smtpServers.setPort(port);
            smtpServers.setUsername(username);
            smtpServers.setPassword(password);
            smtpServers.setHits(hits);
            smtpServers.setPartitionKey(partitionKey);
            smtpServers.setRowKey(rowKey);
            smtpServers.setEtag(etag);
            return smtpServers;
        }
    }
}
