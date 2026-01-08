package uk.gov.moj.cpp.notification.notify.query.api;

import static uk.gov.justice.services.core.annotation.Component.QUERY_API;

import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.notification.notify.query.view.NotificationQueryView;

import javax.inject.Inject;

@ServiceComponent(QUERY_API)
public class NotificationQueryApi {

    @Inject
    private Requester requester;

    @Inject
    private NotificationQueryView notificationQueryView;

    @Handles("notificationnotify.query.notification")
    public JsonEnvelope getNotificationById(final JsonEnvelope query) {
        return notificationQueryView.getNotificationById(query);
    }

    @Handles("notificationnotify.query.find-notification")
    public JsonEnvelope findNotification(final JsonEnvelope query) {
        return notificationQueryView.findNotification(query);
    }
}
