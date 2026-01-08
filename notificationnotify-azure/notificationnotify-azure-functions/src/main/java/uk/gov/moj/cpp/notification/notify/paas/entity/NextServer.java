package uk.gov.moj.cpp.notification.notify.paas.entity;

import com.microsoft.azure.storage.table.TableServiceEntity;

public class NextServer extends TableServiceEntity {

    private int serverId;

    public int getServerId() {
        return serverId;
    }

    public void setServerId(final int serverId) {
        this.serverId = serverId;
    }

    public static final class NextServerBuilder {
        private int serverId;
        private String partitionKey;
        private String rowKey;
        private String etag;

        private NextServerBuilder() {
        }

        public static NextServerBuilder aNextServer() {
            return new NextServerBuilder();
        }

        public NextServerBuilder withServerId(int serverId) {
            this.serverId = serverId;
            return this;
        }

        public NextServerBuilder withPartitionKey(String partitionKey) {
            this.partitionKey = partitionKey;
            return this;
        }

        public NextServerBuilder withRowKey(String rowKey) {
            this.rowKey = rowKey;
            return this;
        }

        public NextServerBuilder withEtag(String etag) {
            this.etag = etag;
            return this;
        }

        public NextServer build() {
           final  NextServer nextServer = new NextServer();
            nextServer.setServerId(serverId);
            nextServer.setPartitionKey(partitionKey);
            nextServer.setRowKey(rowKey);
            nextServer.setEtag(etag);
            return nextServer;
        }
    }
}
