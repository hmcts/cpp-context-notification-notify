package uk.gov.moj.notification.notify.it.stub;

import static javax.ws.rs.core.Response.Status;

public interface NotifyResponseBuilder {

    Status getStatus();
    String build();
}
