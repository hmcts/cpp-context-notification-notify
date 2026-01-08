package uk.gov.moj.cpp.notification.notify.event.processor.task.domain;

import java.time.ZonedDateTime;
import java.util.UUID;

public class NotificationEmailDetails {

    private UUID notificationId;
    private ZonedDateTime sentTime;
    private ZonedDateTime completedAt;
    private String sendToAddress;
    private String replyToAddress;
    private String emailSubject;
    private String emailBody;
    private String clientContext;

    public NotificationEmailDetails(UUID notificationId, ZonedDateTime sentTime, ZonedDateTime completedAt, String sendToAddress,
                                    String replyToAddress, String emailSubject, String emailBody, String clientContext) {
        this.notificationId = notificationId;
        this.sentTime = sentTime;
        this.completedAt = completedAt;
        this.sendToAddress = sendToAddress;
        this.replyToAddress = replyToAddress;
        this.emailSubject = emailSubject;
        this.emailBody = emailBody;
        this.clientContext = clientContext;
    }

    public UUID getNotificationId() {
        return notificationId;
    }

    public ZonedDateTime getSentTime() {
        return sentTime;
    }

    public ZonedDateTime getCompletedAt() {
        return completedAt;
    }

    public String getSendToAddress() {
        return sendToAddress;
    }

    public String getReplyToAddress() {
        return replyToAddress;
    }

    public String getEmailSubject() {
        return emailSubject;
    }

    public String getEmailBody() {
        return emailBody;
    }

    public String getClientContext() {
        return clientContext;
    }

    public static Builder emailDetails() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "NotificationEmailDetails{" +
                "notificationId=" + notificationId +
                ", sentTime=" + sentTime +
                ", completedAt=" + completedAt +
                ", sendToAddress='" + sendToAddress + '\'' +
                ", replyToAddress='" + replyToAddress + '\'' +
                ", emailSubject='" + emailSubject + '\'' +
                ", emailBody='" + emailBody + '\'' +
                ", clientContext='" + clientContext + '\'' +
                '}';
    }

    public static class Builder {
        private UUID notificationId;
        private ZonedDateTime sentTime;
        private ZonedDateTime completedAt;
        private String sendToAddress;
        private String replyToAddress;
        private String emailSubject;
        private String emailBody;
        private String clientContext;

        public Builder withNotificationId(UUID notificationId) {
            this.notificationId = notificationId;
            return this;
        }

        public Builder withSentTime(ZonedDateTime sentTime) {
            this.sentTime = sentTime;
            return this;
        }

        public Builder withCompletedAt(ZonedDateTime completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public Builder withSendToAddress(String sendToAddress) {
            this.sendToAddress = sendToAddress;
            return this;
        }

        public Builder withReplyToAddress(String replyToAddress) {
            this.replyToAddress = replyToAddress;
            return this;
        }

        public Builder withEmailSubject(String emailSubject) {
            this.emailSubject = emailSubject;
            return this;
        }

        public Builder withEmailBody(String emailBody) {
            this.emailBody = emailBody;
            return this;
        }

        public Builder withClientContext(String clientContext) {
            this.clientContext = clientContext;
            return this;
        }

        public NotificationEmailDetails build() {
            return new NotificationEmailDetails(notificationId, sentTime, completedAt, sendToAddress, replyToAddress,
                    emailSubject, emailBody, clientContext);
        }
    }
}

