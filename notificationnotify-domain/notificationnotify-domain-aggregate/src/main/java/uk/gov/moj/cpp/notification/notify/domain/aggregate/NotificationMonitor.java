package uk.gov.moj.cpp.notification.notify.domain.aggregate;

import static java.util.stream.Stream.builder;
import static uk.gov.justice.domain.aggregate.matcher.EventSwitcher.match;
import static uk.gov.justice.domain.aggregate.matcher.EventSwitcher.otherwiseDoNothing;
import static uk.gov.justice.json.schemas.domains.notificationnotify.CheckBouncedEmailRequestFailed.checkBouncedEmailRequestFailed;
import static uk.gov.justice.json.schemas.domains.notificationnotify.CheckPocaEmailRequestFailed.checkPocaEmailRequestFailed;

import uk.gov.justice.domain.aggregate.Aggregate;

import java.util.stream.Stream;

public class NotificationMonitor implements Aggregate {


    private static final long serialVersionUID = 2L;

    public Stream<Object> recordCheckBouncedEmailRequestFailed(final String server, final String reason) {
        final Stream.Builder<Object> builder = builder();
        builder.add(checkBouncedEmailRequestFailed()
                .withServer(server)
                .withReason(reason)
                .build()
        );
        return apply(builder.build());
    }

    public Stream<Object> recordCheckPocaEmailRequestFailed(final String server, final String reason) {
        final Stream.Builder<Object> builder = builder();
        builder.add(checkPocaEmailRequestFailed()
                .withServer(server)
                .withReason(reason)
                .build()
        );
        return apply(builder.build());
    }

    @Override
    public Object apply(Object event) {
        return match(event).with(
                otherwiseDoNothing()
        );
    }

}
