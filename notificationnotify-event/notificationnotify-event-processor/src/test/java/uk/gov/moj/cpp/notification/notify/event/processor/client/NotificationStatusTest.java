package uk.gov.moj.cpp.notification.notify.event.processor.client;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus.CREATED;
import static uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus.NOT_FOUND;
import static uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus.PENDING_VIRUS_CHECK;
import static uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus.PERMANENT_FAILURE;
import static uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus.SENDING;
import static uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus.TEMPORARY_FAILURE;
import static uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus.UNEXPECTED_FAILURE;
import static uk.gov.moj.cpp.notification.notify.event.processor.client.NotificationStatus.VIRUS_SCAN_FAILED;

import org.junit.jupiter.api.Test;

public class NotificationStatusTest {

    @Test
    public void shouldCorrectlyDetermineTheInProgressStates() throws Exception {

        assertThat(CREATED.isInProgress(), is(true));
        assertThat(SENDING.isInProgress(), is(true));
        assertThat(TEMPORARY_FAILURE.isInProgress(), is(true));
        assertThat(PENDING_VIRUS_CHECK.isInProgress(), is(true));
        assertThat(UNEXPECTED_FAILURE.isInProgress(), is(true));
        assertThat(NOT_FOUND.isInProgress(), is(true));
    }

    @Test
    public void shouldCorrectlyDetermineTheFailureStates() throws Exception {

        assertThat(PERMANENT_FAILURE.isFailed(), is(true));
        assertThat(VIRUS_SCAN_FAILED.isFailed(), is(true));
    }
}
