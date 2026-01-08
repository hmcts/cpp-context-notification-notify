package uk.gov.moj.cpp.notification.notify.event.processor.download;

import static java.lang.String.format;
import static javax.ws.rs.core.HttpHeaders.CONTENT_LOCATION;
import static org.apache.commons.io.IOUtils.toByteArray;
import static org.apache.http.HttpStatus.SC_OK;

import uk.gov.moj.cpp.notification.notify.event.processor.metrics.Metrics;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringEscapeUtils;
import org.slf4j.Logger;

@ApplicationScoped
public class DocumentDownloadClient {

    @Inject
    private DownloadRestClient restClient;

    @Inject
    private Metrics metrics;

    @Inject
    @SuppressWarnings("squid:S1312")
    private Logger logger;

    @Inject
    private MediaTypeSelector mediaTypeSelector;

    @SuppressWarnings("squid:S2629")
    public DocumentDownloadResponse getDocument(final String documentUrl) throws IOException {

        logger.info("DocumentDownloadClient document url :: {}", documentUrl);

        final Response response = restClient.download(documentUrl);

        final int responseStatus = response.getStatus();

        logger.info("DocumentDownloadClient response status :: {}", responseStatus);


        if (responseStatus == SC_OK) {
            InputStream stream;
            if("application/vnd.material.query.material+json".equals(mediaTypeSelector.mediaTypeFor(documentUrl))){
                final String azureUrl = response.getLocation().toString();
                stream = new URL(azureUrl).openStream();
            }else{
                stream = response.readEntity(InputStream.class);
            }


            logger.info("DocumentDownloadClient response content location :: {}", response.getHeaderString(CONTENT_LOCATION));

            final String fileName = StringEscapeUtils.escapeJson(response.getHeaderString(CONTENT_LOCATION).split("filename=")[1]);

            logger.info("DocumentDownloadClient fileName :: {}", fileName);


            final byte[] bytes = toByteArray(stream);

            stream.close();

            final InputStream content = new ByteArrayInputStream(bytes);

            return new SuccessfulDocumentDownload(responseStatus, content, bytes.length, bytes, fileName);
        }

        metrics.incrementLetterFailedToDownload();

        final String errorMessage = format("Failed to download PDF document: Response code '%d'", responseStatus);
        logger.error(errorMessage);

        return new UnsuccessfulDocumentDownload(responseStatus, errorMessage);
    }
}
