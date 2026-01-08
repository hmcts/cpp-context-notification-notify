package uk.gov.moj.cpp.notification.notify.paas;

import java.util.ArrayList;
import java.util.List;

public class RetrySettings {
    private ArrayList<RetryDefinition> retryDefinitions;

    public List<RetryDefinition> getRetryDefinitions() {
        return new ArrayList<>(this.retryDefinitions);
    }

    public void setRetryDefinitions(List<RetryDefinition> retryDefinitions) {
        this.retryDefinitions = new ArrayList<>(retryDefinitions);
    }
}