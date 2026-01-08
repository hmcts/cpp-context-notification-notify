package uk.gov.moj.cpp.notification.repository;

import uk.gov.moj.cpp.notification.entity.Notification;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.deltaspike.data.api.EntityRepository;
import org.apache.deltaspike.data.api.Repository;
import org.apache.deltaspike.data.api.criteria.CriteriaSupport;

@Repository
@ApplicationScoped
public abstract class NotificationRepository implements EntityRepository<Notification, UUID>, CriteriaSupport<Notification> {

    public static final String STATUS = "status";
    public static final String SEND_TO_ADDRESS = "sendToAddress";
    public static final String STATUS_CODE = "statusCode";
    public static final String CREATED_AFTER = "createdAfter";
    public static final String CREATED_BEFORE = "createdBefore";
    public static final String DATE_CREATED = "dateCreated";
    public static final String NOTIFICATION_TYPE = "notificationType";
    public static final String LETTER_URL = "letterUrl";
    public static final String MATERIAL_URL = "materialUrl";

    @Inject
    private EntityManager entityManager;

    public List<Notification> findNotifications(final Map<String, Object> queryParameters) {

        final CriteriaBuilder qb = entityManager.getCriteriaBuilder();
        final CriteriaQuery cq = qb.createQuery();
        final Root<Notification> notification = cq.from(Notification.class);

        final List<Predicate> predicates = new ArrayList<>();

        if (queryParameters.containsKey(STATUS)) {
            predicates.add(qb.equal(notification.get(STATUS), queryParameters.get(STATUS)));
        }

        if (queryParameters.containsKey(SEND_TO_ADDRESS)) {
            predicates.add(qb.equal(notification.get(SEND_TO_ADDRESS), queryParameters.get(SEND_TO_ADDRESS)));
        }

        if (queryParameters.containsKey(STATUS_CODE)) {
            predicates.add(qb.equal(notification.get(STATUS_CODE), queryParameters.get(STATUS_CODE)));
        }

        if (queryParameters.containsKey(CREATED_AFTER)) {
            predicates.add(qb.greaterThanOrEqualTo(notification.get(DATE_CREATED), (ZonedDateTime) queryParameters.get(CREATED_AFTER)));
        }

        if (queryParameters.containsKey(CREATED_BEFORE)) {
            predicates.add(qb.lessThanOrEqualTo(notification.get(DATE_CREATED), (ZonedDateTime) queryParameters.get(CREATED_BEFORE)));
        }

        if (queryParameters.containsKey(NOTIFICATION_TYPE)) {
            predicates.add(qb.equal(notification.get(NOTIFICATION_TYPE), queryParameters.get(NOTIFICATION_TYPE)));
        }

        if (queryParameters.containsKey(LETTER_URL)) {
            predicates.add(qb.equal(notification.get(LETTER_URL), queryParameters.get(LETTER_URL)));
        }

        if (queryParameters.containsKey(MATERIAL_URL)) {
            predicates.add(qb.equal(notification.get(MATERIAL_URL), queryParameters.get(MATERIAL_URL)));
        }

        cq.select(notification).where(predicates.toArray(new Predicate[]{}));

        return entityManager.createQuery(cq).getResultList();
    }
}