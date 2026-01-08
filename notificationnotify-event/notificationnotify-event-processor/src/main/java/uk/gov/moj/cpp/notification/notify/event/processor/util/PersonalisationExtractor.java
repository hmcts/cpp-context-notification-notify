package uk.gov.moj.cpp.notification.notify.event.processor.util;

import static com.google.common.io.ByteStreams.toByteArray;
import static java.util.stream.Collectors.toMap;

import uk.gov.justice.json.schemas.domains.notificationnotify.Personalisation;
import uk.gov.moj.cpp.notification.notify.event.processor.download.SuccessfulDocumentDownload;
import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.SendEmailDetails;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FilenameUtils;
import org.json.JSONObject;


public class PersonalisationExtractor {

    private static final String MATERIAL_URL = "material_url";
    private static final String CSV_EXTENSION = "csv";

    public Map<String, Object> extractFrom(final SendEmailDetails sendEmailDetails,
                                           final SuccessfulDocumentDownload successfulDocumentDownload)
            throws IOException {
        final Map<String, Object> personalisation = extractFrom(sendEmailDetails);

        try (final InputStream emailInputStream = successfulDocumentDownload.getContent()) {
            final byte[] documentContents = toByteArray(emailInputStream);
            final boolean isCSV = FilenameUtils.isExtension(successfulDocumentDownload.getFileName(), CSV_EXTENSION);
            personalisation.put(MATERIAL_URL, prepareUpload(documentContents, isCSV));
        }

        return personalisation;
    }

    public Map<String, Object> extractFrom(final SendEmailDetails sendEmailDetails) {

        final Optional<Personalisation> personalisation = sendEmailDetails.getPersonalisation();
        if (personalisation.isPresent() && personalisation.get().getAdditionalProperties() != null
                && personalisation.get().getAdditionalProperties().size() != 0) {
            return personalisation.get()
                    .getAdditionalProperties()
                    .entrySet().stream()
                    .collect(toMap(
                            Entry::getKey,
                            Entry::getValue));
        }
        return new HashMap<>();
    }

    private static JSONObject prepareUpload(final byte[] documentContents, boolean isCsv) {
        final byte[] fileContentAsByte = Base64.encodeBase64(documentContents);
        final String fileContent = new String(fileContentAsByte, StandardCharsets.ISO_8859_1);
        final JSONObject jsonFileObject = new JSONObject();
        jsonFileObject.put("file", fileContent);
        jsonFileObject.put("is_csv", isCsv);
        return jsonFileObject;
    }
}
