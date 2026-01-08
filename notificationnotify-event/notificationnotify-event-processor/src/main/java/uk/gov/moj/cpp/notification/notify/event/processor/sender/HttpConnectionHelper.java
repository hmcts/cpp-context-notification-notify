package uk.gov.moj.cpp.notification.notify.event.processor.sender;

import uk.gov.moj.cpp.notification.notify.event.processor.client.MicroSoftOfficeClientNotificationException;
import uk.gov.moj.cpp.notification.notify.event.processor.client.Office365EmailResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpConnectionHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpConnectionHelper.class);

    private static final String CONTENT_TYPE = "content-type";
    private static final String APPLICATION_JSON_CONTENT_TYPE = "application/json";
    private static final String OCP_APIM_SUBSCRIPTION_KEY = "Ocp-Apim-Subscription-Key";

    @SuppressWarnings({"squid:S1160", "squid:S1162"})
    public Office365EmailResponse getResponseCode(final String url, final JSONObject payload, final String subscriptionKey) throws MicroSoftOfficeClientNotificationException, IOException {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("URL for Microsoft Office 365 :: {} with templateId : {}, emailAddress : {}, reference(notificationId) : {}  personalisation : {}"
                    , url, payload.get("templateId"), payload.get("emailAddress"), payload.get("reference"), payload.get("personalisation"));
        }
        final HttpPost post = new HttpPost(url);
        post.addHeader(CONTENT_TYPE, APPLICATION_JSON_CONTENT_TYPE);
        post.addHeader(OCP_APIM_SUBSCRIPTION_KEY, subscriptionKey);
        post.setEntity(new StringEntity(payload.toString()));

        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(post)) {
            StringBuilder sb;
            if (response.getStatusLine().getStatusCode() != 200) {
                sb = this.readStream(new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8));
                throw new MicroSoftOfficeClientNotificationException(response.getStatusLine().getStatusCode(), sb.toString());
            }

            sb = this.readStream(new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8));
            return new Office365EmailResponse(sb.toString());
        }
    }

    private StringBuilder readStream(InputStreamReader streamReader) throws IOException {
        final StringBuilder sb = new StringBuilder();
        final BufferedReader br = new BufferedReader(streamReader);

        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line).append("\n");
        }

        br.close();
        return sb;
    }
}

