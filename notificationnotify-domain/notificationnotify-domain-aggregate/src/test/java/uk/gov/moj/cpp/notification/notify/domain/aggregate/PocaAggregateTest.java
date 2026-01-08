package uk.gov.moj.cpp.notification.notify.domain.aggregate;

import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import uk.gov.justice.json.schemas.domains.notificationnotify.PocaEmailAlreadyReceived;
import uk.gov.justice.json.schemas.domains.notificationnotify.PocaEmailNotificationReceived;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

public class PocaAggregateTest {

    @Test
    public void shouldProcessPocaEmail() {

        final UUID pocaFileId = randomUUID();
        final UUID pocaMailId = randomUUID();

        PocaAggregate pocaAggregate = new PocaAggregate();
        final Stream<Object> processPocaEmail = pocaAggregate.processPocaEmail(pocaFileId, pocaMailId, "test@test.com", "test subject");
        final List eventsList = processPocaEmail.collect(toList());
        final Object event = eventsList.get(0);
        assertThat(eventsList.size(), is(1));
        assertThat(PocaEmailNotificationReceived.class, equalTo(event.getClass()));
        final PocaEmailNotificationReceived pocaEmailNotificationReceived = (PocaEmailNotificationReceived) event;
        assertThat(pocaEmailNotificationReceived.getPocaFileId(), is(pocaFileId));
        assertThat(pocaEmailNotificationReceived.getPocaMailId(), is(pocaMailId));
        assertThat(pocaEmailNotificationReceived.getPocaEmail(), is("test@test.com"));
    }

    @Test
    public void shouldRaisePocaEmailAlreadyReceivedWhenPocaEmailAlreadyProcessed() {

        final UUID pocaFileId = randomUUID();
        final UUID pocaMailId = randomUUID();

        PocaAggregate pocaAggregate = new PocaAggregate();
        pocaAggregate.processPocaEmail(pocaFileId, pocaMailId, "test@test.com", "test subject");
        final Stream<Object> processPocaEmail = pocaAggregate.processPocaEmail(pocaFileId, pocaMailId, "test@test.com", "test subject");
        final List eventsList = processPocaEmail.collect(toList());
        final Object event = eventsList.get(0);
        assertThat(eventsList.size(), is(1));
        assertEquals(PocaEmailAlreadyReceived.class, event.getClass());
        final PocaEmailAlreadyReceived pocaEmailAlreadyReceived = (PocaEmailAlreadyReceived) event;
        assertThat(pocaEmailAlreadyReceived.getPocaFileId(), is(pocaFileId));
    }
}