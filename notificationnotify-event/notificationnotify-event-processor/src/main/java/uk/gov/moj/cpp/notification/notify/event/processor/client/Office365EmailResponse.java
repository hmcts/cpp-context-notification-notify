package uk.gov.moj.cpp.notification.notify.event.processor.client;

import java.util.UUID;

import org.json.JSONObject;

public class Office365EmailResponse {

    private final UUID notificationId;
    private final JSONObject data;

    public Office365EmailResponse(String response) {
        this.data = new JSONObject(response);
        this.notificationId = UUID.fromString(data.getString("reference"));
    }

    public UUID getNotificationId() {
        return notificationId;
    }
    public JSONObject getData() {
        return data;
    }
    @Override
    public String toString() {
        return "Office365EmailResponse{" +
                "notificationId=" + notificationId +
                '}';
    }
}
