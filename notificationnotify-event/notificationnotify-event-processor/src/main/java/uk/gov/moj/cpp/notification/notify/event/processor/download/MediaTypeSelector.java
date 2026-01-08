package uk.gov.moj.cpp.notification.notify.event.processor.download;

import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MediaTypeSelector {

    public String mediaTypeFor(final String url) {
        if (url.contains("material-query-api")) {
            return "application/vnd.material.query.material+json";
        }

        return APPLICATION_OCTET_STREAM;
    }
}
