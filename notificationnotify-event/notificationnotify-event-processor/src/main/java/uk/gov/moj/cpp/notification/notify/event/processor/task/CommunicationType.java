package uk.gov.moj.cpp.notification.notify.event.processor.task;


public enum CommunicationType {
    EMAIL("Email"),
    LETTER("Letter");

    private String type;

    CommunicationType(final String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}