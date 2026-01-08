package uk.gov.moj.cpp.notification.notify.event.processor.download;

import static uk.gov.justice.services.common.http.HeaderConstants.USER_ID;

import uk.gov.moj.cpp.systemusers.ServiceContextSystemUserProvider;

import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.core.Response;

@ApplicationScoped
public class DownloadRestClient {

    @Inject
    private ServiceContextSystemUserProvider serviceContextSystemUserProvider;

    @Inject
    private MediaTypeSelector mediaTypeSelector;

    @Inject
    private ClientCreator clientCreator;

    public Response download(final String documentUrl) {

        final UUID systemUserId = serviceContextSystemUserProvider
                .getContextSystemUserId()
                .orElseThrow(() -> new DownloadClientException("Failed to retrieve System User Id"));

        return clientCreator.createNewClient()
                .target(documentUrl)
                .request()
                .accept(mediaTypeSelector.mediaTypeFor(documentUrl))
                .header(USER_ID, systemUserId.toString())
                .get();
    }
}
