package uk.gov.moj.cpp.notification.entity;

import java.time.ZonedDateTime;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @Column(name = "notification_id", nullable = false)
    private UUID notificationId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "send_to_address")
    private String sendToAddress;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "date_created", nullable = false)
    private ZonedDateTime dateCreated;

    @Column(name = "last_updated", nullable = false)
    private ZonedDateTime lastUpdated;

    @Column(name = "notification_type", nullable = false)
    private String notificationType;

    @Column(name = "letter_url")
    private String letterUrl;

    @Column(name = "material_url")
    private String materialUrl;

    public Notification() {}

    public Notification(final UUID notificationId, final NotificationType notificationType) {
        this.notificationId = notificationId;
        this.notificationType = notificationType.name();
    }

    public UUID getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(UUID notificationId) {
        this.notificationId = notificationId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public ZonedDateTime getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(ZonedDateTime dateCreated) {
        this.dateCreated = dateCreated;
    }

    public ZonedDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(ZonedDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getSendToAddress() {
        return sendToAddress;
    }

    public void setSendToAddress(String sendToAddress) {
        this.sendToAddress = sendToAddress;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(final String notificationType) {
        this.notificationType = notificationType;
    }

    public String getLetterUrl() {
        return letterUrl;
    }

    public void setLetterUrl(final String letterUrl) {
        this.letterUrl = letterUrl;
    }

    public String getMaterialUrl() {
        return materialUrl;
    }

    public void setMaterialUrl(final String materialUrl) {
        this.materialUrl = materialUrl;
    }
}
