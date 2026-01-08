package uk.gov.moj.cpp.notification.notify.paas;

import java.util.ArrayList;
import java.util.List;

public class OutlookSettings {
    private ArrayList<OutlookCredentials> outlookCredentials;

    public List<OutlookCredentials> getOutlookCredentials() {
        return new ArrayList<>(this.outlookCredentials);
    }

    public void setOutlookCredentials(List<OutlookCredentials> outlookCredentials) {
        this.outlookCredentials = new ArrayList<>(outlookCredentials);
    }
}