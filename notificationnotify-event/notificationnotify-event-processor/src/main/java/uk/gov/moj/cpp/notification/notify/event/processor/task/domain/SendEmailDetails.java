package uk.gov.moj.cpp.notification.notify.event.processor.task.domain;

import uk.gov.justice.json.schemas.domains.notificationnotify.Personalisation;

import java.util.Optional;
import java.util.UUID;

public class SendEmailDetails {

    private final UUID templateId;
    private final String sendToAddress;
    private final Optional<String> replyToAddress;
    private final Optional<UUID> replyToAddressId;
    private final Optional<Personalisation> personalisation;
    private final Optional<String> materialUrl;
    private final Optional<UUID> fileId;


    public SendEmailDetails(final UUID templateId,
                            final String sendToAddress,
                            final Optional<String> replyToAddress,
                            final Optional<UUID> replyToAddressId,
                            final Optional<Personalisation> personalisation,
                            final Optional<String> materialUrl,
                            final Optional<UUID> fileId) {
        this.templateId = templateId;
        this.sendToAddress = sendToAddress;
        this.replyToAddress = replyToAddress;
        this.replyToAddressId = replyToAddressId;
        this.personalisation = personalisation;
        this.materialUrl = materialUrl;
        this.fileId = fileId;
    }


    public UUID getTemplateId() {
        return templateId;
    }

    public String getSendToAddress() {
        return sendToAddress;
    }

    public Optional<String> getReplyToAddress() {
        return replyToAddress;
    }

    public Optional<UUID> getReplyToAddressId() {
        return replyToAddressId;
    }

    public Optional<Personalisation> getPersonalisation() {
        return personalisation;
    }

    public Optional<String> getMaterialUrl() {
        return materialUrl;
    }

    public Optional<UUID> getFileId() {
        return fileId;
    }

    @Override
    public String toString() {
        return "SendEmailDetails{" +
                "templateId=" + templateId +
                ", sendToAddress='" + sendToAddress + '\'' +
                ", replyToAddress=" + replyToAddress +
                ", replyToAddressId=" + replyToAddressId +
                ", personalisation=" + personalisation +
                ", materialUrl=" + materialUrl +
                ", fileId=" + fileId +
                '}';
    }
}
