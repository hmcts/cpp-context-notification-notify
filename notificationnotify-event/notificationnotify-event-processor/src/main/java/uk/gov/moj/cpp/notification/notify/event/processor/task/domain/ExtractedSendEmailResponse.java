package uk.gov.moj.cpp.notification.notify.event.processor.task.domain;

public class ExtractedSendEmailResponse {
    private final boolean is365Email;
    private final String emailSubject;
    private final String emailBody;
    private final String emailReplyToAddress;
    private final String emailSendToAddress;

    public ExtractedSendEmailResponse(boolean is365Email, String emailSubject, String emailBody, String emailReplyToAddress,
                                      String emailSendToAddress) {
        this.is365Email = is365Email;
        this.emailBody = emailBody;
        this.emailSubject = emailSubject;
        this.emailReplyToAddress = emailReplyToAddress;
        this.emailSendToAddress = emailSendToAddress;
    }

    public boolean isIs365Email() {
        return is365Email;
    }
    public String getEmailBody() {
        return emailBody;
    }
    public String getEmailSubject() {
        return emailSubject;
    }

    public String getEmailReplyToAddress() {
        return emailReplyToAddress;
    }

    public String getEmailSendToAddress() {
        return emailSendToAddress;
    }
}
