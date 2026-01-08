package uk.gov.moj.cpp.notification.notify.paas;

public class RetryDefinition {
    private int retryNumber;
    private int duration;

    public RetryDefinition() {
        this.retryNumber = 1;
        this.duration = 0;
    }

    public int getRetryNumber() {
        return retryNumber;
    }

    public void setRetryNumber(final int retryNumber) {
        this.retryNumber = retryNumber;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(final int duration) {
        this.duration = duration;
    }
}
