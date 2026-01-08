package uk.gov.moj.notification.notify.it.util;

import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static uk.gov.moj.notification.notify.it.util.FrameworkConstants.CONTEXT_NAME;

import uk.gov.justice.services.eventsourcing.repository.jdbc.event.Event;
import uk.gov.justice.services.test.utils.core.messaging.Poller;
import uk.gov.justice.services.test.utils.events.EventStoreDataAccess;
import uk.gov.justice.services.test.utils.persistence.TestJdbcDataSourceProvider;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class EventFetcher {

    public List<Event> getEventsByStreamAndSize(final UUID notificationId, final int expectedNumberOfEvents) {
        final Poller poller = new Poller(100, 1000L);

        final Optional<List<Event>> eventsListOptional = poller.pollUntilFound(() -> {
            final List<Event> events = eventsOfStreamId(notificationId);
            if (events.size() >= expectedNumberOfEvents) {
                return of(events);
            }
            return empty();
        });

        return eventsListOptional.orElse(emptyList());

    }

    private List<Event> eventsOfStreamId(final UUID uploadId) {

        final EventStoreDataAccess eventStoreDataAccess = new EventStoreDataAccess(new TestJdbcDataSourceProvider().getEventStoreDataSource(CONTEXT_NAME));

        final List<Event> events = eventStoreDataAccess.findEventsByStream(uploadId);

        events.sort(comparing(Event::getPositionInStream));

        return events;
    }
}
