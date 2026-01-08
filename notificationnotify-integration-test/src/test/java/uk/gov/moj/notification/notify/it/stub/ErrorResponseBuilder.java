package uk.gov.moj.notification.notify.it.stub;

import static javax.json.Json.createArrayBuilder;
import static javax.json.Json.createObjectBuilder;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

import javax.json.JsonObjectBuilder;
import javax.ws.rs.core.Response.Status;

public class ErrorResponseBuilder implements NotifyResponseBuilder {

    private String message = "Template not found";
    private String error = "BadRequestError";
    private Status status = BAD_REQUEST;

    public static ErrorResponseBuilder anErrorResponse() {
        return new ErrorResponseBuilder();
    }

    public ErrorResponseBuilder withMessage(final String message) {
        this.message = message;
        return this;
    }

    public ErrorResponseBuilder withError(final String error) {
        this.error = error;
        return this;
    }

    public ErrorResponseBuilder withStatus(final Status status) {
        this.status = status;
        return this;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public Status getStatus() {
        return status;
    }

    public String build() {

        final JsonObjectBuilder error_1 = createObjectBuilder()
                .add("error", error)
                .add("message", message);


        return createObjectBuilder()
                .add("errors", createArrayBuilder()
                        .add(error_1)
                        .build())
                .add("status_code", status.getStatusCode())
                .build().toString();
    }
}
