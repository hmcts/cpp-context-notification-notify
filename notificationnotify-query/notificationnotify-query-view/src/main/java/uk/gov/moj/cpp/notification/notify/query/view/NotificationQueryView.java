package uk.gov.moj.cpp.notification.notify.query.view;

import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;

import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.ZonedDateTimes;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.notification.entity.Notification;
import uk.gov.moj.cpp.notification.repository.NotificationRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;
import uk.gov.justice.services.messaging.JsonObjects;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;

public class NotificationQueryView {

    private static final String QUERY_PARAM_STATUS = "status";
    private static final String QUERY_PARAM_NOTIFICATION_ID = "notificationId";
    private static final String QUERY_PARAM_SEND_TO_ADDRESS = "sendToAddress";
    private static final String QUERY_PARAM_CREATED_BEFORE = "createdBefore";
    private static final String QUERY_PARAM_CREATED_AFTER = "createdAfter";
    private static final String QUERY_PARAM_STATUS_CODE = "statusCode";
    private static final String NOTIFICATION_TYPE = "notificationType";
    private static final String LETTER_URL = "letterUrl";
    private static final String MATERIAL_URL = "materialUrl";

    @Inject
    private Enveloper enveloper;

    @Inject
    ObjectToJsonObjectConverter converter;

    @Inject
    private NotificationRepository notificationRepository;

    public JsonEnvelope getNotificationById(final JsonEnvelope query) {
        final UUID notificationId = UUID.fromString(query.payloadAsJsonObject().getString(QUERY_PARAM_NOTIFICATION_ID));

        final Notification notification = notificationRepository.findBy(notificationId);

        return notificationResponseFor(query, notification != null ? converter.convert(notification) : null);
    }

    public JsonEnvelope findNotification(final JsonEnvelope query) {

        final Map<String, Object> queryMap = buildQueryMap(query);

        final List<Notification> results = notificationRepository.findNotifications(queryMap);

        if (!results.isEmpty()) {
            final JsonArrayBuilder responseBuilder = createArrayBuilder();

            for (final Notification notification : results) {
                responseBuilder.add(converter.convert(notification));
            }

            final JsonObject responsePayload = JsonObjects.createObjectBuilder().add("notifications", responseBuilder).build();
            return notificationsResponseFor(query, responsePayload);
        } else {
            return notificationsResponseFor(query, null);
        }
    }

    private Map<String, Object> buildQueryMap(final JsonEnvelope query) {
        final JsonObject queryPayload = query.payloadAsJsonObject();
        final Map<String, Object> queryMap = new HashMap<>();

        if (queryPayload.containsKey(QUERY_PARAM_STATUS)) {
            queryMap.put(QUERY_PARAM_STATUS, queryPayload.getString(QUERY_PARAM_STATUS));
        }

        if (queryPayload.containsKey(QUERY_PARAM_SEND_TO_ADDRESS)) {
            queryMap.put(QUERY_PARAM_SEND_TO_ADDRESS, queryPayload.getString(QUERY_PARAM_SEND_TO_ADDRESS));
        }

        if (queryPayload.containsKey(QUERY_PARAM_CREATED_BEFORE)) {
            queryMap.put(QUERY_PARAM_CREATED_BEFORE, ZonedDateTimes.fromString(queryPayload.getString(QUERY_PARAM_CREATED_BEFORE)));
        }

        if (queryPayload.containsKey(QUERY_PARAM_CREATED_AFTER)) {
            queryMap.put(QUERY_PARAM_CREATED_AFTER, ZonedDateTimes.fromString(queryPayload.getString(QUERY_PARAM_CREATED_AFTER)));
        }

        if (queryPayload.containsKey(QUERY_PARAM_STATUS_CODE)) {
            queryMap.put(QUERY_PARAM_STATUS_CODE, queryPayload.getInt(QUERY_PARAM_STATUS_CODE));
        }

        if (queryPayload.containsKey(NOTIFICATION_TYPE)) {
            queryMap.put(NOTIFICATION_TYPE, queryPayload.getString(NOTIFICATION_TYPE));
        }

        if (queryPayload.containsKey(LETTER_URL)) {
            queryMap.put(LETTER_URL, queryPayload.getString(LETTER_URL));
        }

        if (queryPayload.containsKey(MATERIAL_URL)) {
            queryMap.put(MATERIAL_URL, queryPayload.getString(MATERIAL_URL));
        }

        return queryMap;
    }

    private JsonEnvelope notificationResponseFor(final JsonEnvelope query, final Object payload) {
        return responseFor(query, "notificationnotify.query.notification", payload);
    }

    private JsonEnvelope notificationsResponseFor(final JsonEnvelope query, final Object payload) {
        return responseFor(query, "notificationnotify.query.notification2", payload);
    }

    private JsonEnvelope responseFor(final JsonEnvelope query, final String responseName, final Object payload) {
        return enveloper.withMetadataFrom(query, responseName).apply(payload);
    }
}
