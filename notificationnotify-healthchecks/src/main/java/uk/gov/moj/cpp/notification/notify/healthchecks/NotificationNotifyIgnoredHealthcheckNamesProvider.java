package uk.gov.moj.cpp.notification.notify.healthchecks;

import static java.util.List.of;
import static uk.gov.justice.services.healthcheck.healthchecks.FileStoreHealthcheck.FILE_STORE_HEALTHCHECK_NAME;

import uk.gov.justice.services.healthcheck.api.DefaultIgnoredHealthcheckNamesProvider;

import java.util.List;

import javax.enterprise.inject.Specializes;

@Specializes
public class NotificationNotifyIgnoredHealthcheckNamesProvider extends DefaultIgnoredHealthcheckNamesProvider {

    public NotificationNotifyIgnoredHealthcheckNamesProvider() {
        // This constructor is required by CDI.
    }

    @Override
    public List<String> getNamesOfIgnoredHealthChecks() {
        return of(FILE_STORE_HEALTHCHECK_NAME);
    }
}