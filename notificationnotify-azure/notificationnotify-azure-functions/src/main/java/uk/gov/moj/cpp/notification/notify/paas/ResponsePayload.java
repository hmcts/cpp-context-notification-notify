package uk.gov.moj.cpp.notification.notify.paas;

public class ResponsePayload {

    private final String templateId;
    private final String htmlBody;
    private final String reference;
    private final String subject;
    private final String templateContent;

    private ResponsePayload(final Builder builder) {
        this.templateId = builder.templateId;
        this.htmlBody = builder.htmlBody;
        this.reference = builder.reference;
        this.subject = builder.subject;
        this.templateContent = builder.templateContent;
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getHtmlBody() {
        return htmlBody;
    }

    public static Builder createResponsePayloadBuilder() {
        return new Builder();
    }

    public String getReference() {
        return reference;
    }

    public String getSubject() {
        return subject;
    }

    public String getTemplateContent() {
        return templateContent;
    }

    public static final class Builder {
        private String templateId;
        private String htmlBody;
        private String reference;
        private String subject;
        private String templateContent;

        public Builder withTemplateId(final String templateId) {
            this.templateId = templateId;
            return this;
        }

        public Builder withHtmlBody(final String htmlBody) {
            this.htmlBody = htmlBody;
            return this;
        }

        public Builder withReference(final String reference) {
            this.reference = reference;
            return this;
        }

        public Builder withSubject(final String subject) {
            this.subject = subject;
            return this;
        }

        public Builder withTemplateContent(final String templateContent) {
            this.templateContent = templateContent;
            return this;
        }

        public ResponsePayload build() {
            return new ResponsePayload(this);
        }
    }
}