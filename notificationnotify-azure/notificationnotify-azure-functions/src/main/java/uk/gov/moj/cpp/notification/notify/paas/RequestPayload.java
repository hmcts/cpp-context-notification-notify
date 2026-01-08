package uk.gov.moj.cpp.notification.notify.paas;

import java.util.Map;

public class RequestPayload {
    private Map<String, String> personalisation;
    private String reference;
    private String emailAddress;
    private String templateId;
    private String replyToAddressId;
    private Attachment attachment;

    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getReplyToAddressId() {
        return replyToAddressId;
    }

    public void setReplyToAddressId(String replyToAddressId) {
        this.replyToAddressId = replyToAddressId;
    }

    public Attachment getAttachment() {
        return attachment;
    }

    public void setAttachment(Attachment attachment) {
        this.attachment = attachment;
    }

    public Map<String, String> getPersonalisation() {
        return personalisation;
    }

    public void setPersonalisation(final Map<String, String> personalisation) {
        this.personalisation = personalisation;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(final String reference) {
        this.reference = reference;
    }


}