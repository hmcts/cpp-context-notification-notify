package uk.gov.moj.cpp.notification.notify.event.processor.task.domain;

import java.util.ArrayList;
import java.util.List;

public class MailServerSettings {
    private ArrayList<MailServerCredentials> mailServerCredentials;

    public List<MailServerCredentials> getMailServerCredentials() {
        return new ArrayList<>(this.mailServerCredentials);
    }

    public void setMailServerCredentials(List<MailServerCredentials> mailServerCredentials) {
        this.mailServerCredentials = new ArrayList<>(mailServerCredentials);
    }
}