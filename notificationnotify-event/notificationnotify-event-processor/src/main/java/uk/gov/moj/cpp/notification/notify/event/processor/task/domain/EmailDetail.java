package uk.gov.moj.cpp.notification.notify.event.processor.task.domain;

import java.io.InputStream;
import java.util.UUID;

import javax.mail.Message;

public class EmailDetail {

    private final Message message;
    private UUID notificationId;
    private UUID pocaMailId;
    private InputStream documentContent;
    private String senderEmail;
    private String fileName;
    private String subject;

    public EmailDetail(final Message message) {
        this.message = message;
    }

    public EmailDetail(final Message message, final UUID notificationId) {
        this.message = message;
        this.notificationId = notificationId;
    }

    public EmailDetail(Message message, UUID notificationId, UUID pocaMailId, InputStream documentContent, String senderEmail, String fileName, String subject) {
        this.message = message;
        this.notificationId = notificationId;
        this.pocaMailId = pocaMailId;
        this.documentContent = documentContent;
        this.senderEmail = senderEmail;
        this.fileName = fileName;
        this.subject = subject;
    }

    public Message getMessage() {
        return message;
    }

    public UUID getNotificationId() {
        return notificationId;
    }

    public UUID getPocaMailId() {
        return pocaMailId;
    }

    public InputStream getDocumentContent() {
        return documentContent;
    }

    public String getSenderEmail() {
        return senderEmail;
    }

    public String getFileName() {
        return fileName;
    }

    public String getSubject() {
        return subject;
    }

    public static Builder emailDetails(){
        return new EmailDetail.Builder();
    }

    @Override
    public String toString() {
        return "NotificationEmailDetails{" +
                "message=" + message +
                ", notificationId=" + notificationId +
                ", pocaMailId=" + pocaMailId +
                ", documentContent=" + documentContent +
                ", senderEmail='" + senderEmail + '\'' +
                ", fileName='" + fileName + '\'' +
                ", subject='" + subject + '\'' +
                '}';
    }


    public static class Builder {

        private Message message;
        private UUID notificationId;
        private UUID pocaMailId;
        private InputStream documentContent;
        private String senderEmail;
        private String fileName;
        private String subject;


        public Builder withMessage(Message message) {
            this.message = message;
            return this;
        }

        public Builder withNotificationId(UUID notificationId) {
            this.notificationId = notificationId;
            return this;
        }

        public Builder withPocaMailId(UUID pocaMailId) {
            this.pocaMailId = pocaMailId;
            return this;
        }

        public Builder withDocumentContent(InputStream documentContent) {
            this.documentContent = documentContent;
            return this;
        }

        public Builder withSenderEmail(String senderEmail) {
            this.senderEmail = senderEmail;
            return this;
        }

        public Builder withFileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder withSubject(String subject) {
            this.subject = subject;
            return this;
        }

        public EmailDetail build() {
            return new EmailDetail( message,  notificationId, pocaMailId,  documentContent, senderEmail,  fileName, subject);
        }
    }
}
