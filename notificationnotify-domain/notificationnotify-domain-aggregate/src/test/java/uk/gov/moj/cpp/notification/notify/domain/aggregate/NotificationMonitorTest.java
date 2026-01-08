package uk.gov.moj.cpp.notification.notify.domain.aggregate;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import uk.gov.justice.json.schemas.domains.notificationnotify.CheckBouncedEmailRequestFailed;
import uk.gov.justice.json.schemas.domains.notificationnotify.CheckPocaEmailRequestFailed;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class NotificationMonitorTest {

    @InjectMocks
    NotificationMonitor notificationMonitor;

    @Test
    public void shouldRecordCheckBouncedEmailRequestFailed() {

        final Stream<Object> recordCheckBouncedEmailRequestFailed = notificationMonitor.recordCheckBouncedEmailRequestFailed("outlook.office365.com", "server down");
        final List<Object> eventsList = recordCheckBouncedEmailRequestFailed.collect(toList());
        final Object event = eventsList.get(0);
        final CheckBouncedEmailRequestFailed checkBouncedEmailRequestFailed = (CheckBouncedEmailRequestFailed) event;

        assertThat(eventsList.size(), is(1));
        assertEquals(CheckBouncedEmailRequestFailed.class, event.getClass());
        assertThat(checkBouncedEmailRequestFailed.getReason(), is("server down"));
        assertThat(checkBouncedEmailRequestFailed.getServer(), is("outlook.office365.com"));
    }

    @Test
    public void shouldRecordCheckPocaEmailRequestFailed() {

        final List<Object> eventsList = notificationMonitor
                .recordCheckPocaEmailRequestFailed("outlook.office365.com", "server down")
                .toList();
        final Object event = eventsList.get(0);
        final CheckPocaEmailRequestFailed checkPocaEmailRequestFailed = (CheckPocaEmailRequestFailed) event;

        assertThat(eventsList.size(), is(1));
        assertEquals(CheckPocaEmailRequestFailed.class, event.getClass());
        assertThat(checkPocaEmailRequestFailed.getReason(), is("server down"));
        assertThat(checkPocaEmailRequestFailed.getServer(), is("outlook.office365.com"));
    }


}
