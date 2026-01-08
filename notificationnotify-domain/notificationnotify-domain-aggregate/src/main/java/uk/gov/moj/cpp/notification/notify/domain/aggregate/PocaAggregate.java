package uk.gov.moj.cpp.notification.notify.domain.aggregate;

import static java.util.stream.Stream.of;
import static uk.gov.justice.domain.aggregate.matcher.EventSwitcher.match;
import static uk.gov.justice.domain.aggregate.matcher.EventSwitcher.otherwiseDoNothing;
import static uk.gov.justice.domain.aggregate.matcher.EventSwitcher.when;
import static uk.gov.justice.json.schemas.domains.notificationnotify.PocaEmailAlreadyReceived.pocaEmailAlreadyReceived;
import static uk.gov.justice.json.schemas.domains.notificationnotify.PocaEmailNotificationReceived.pocaEmailNotificationReceived;

import uk.gov.justice.domain.aggregate.Aggregate;
import uk.gov.justice.json.schemas.domains.notificationnotify.PocaEmailNotificationReceived;

import java.util.UUID;
import java.util.stream.Stream;

public class PocaAggregate implements Aggregate {

    private static final long serialVersionUID = 2L;
    private boolean isPocaEmailNotified;

    @Override
    public Object apply(Object event) {
        return match(event).with(
                when(PocaEmailNotificationReceived.class).apply(pocaEmailNotificationReceived -> this.isPocaEmailNotified = true),
                otherwiseDoNothing()
        );
    }

    public Stream<Object> processPocaEmail(UUID pocaFileId, UUID pocaMailId, String senderEmail, String emailSubject) {
        if (!isPocaEmailNotified) {
            return apply(of(pocaEmailNotificationReceived()
                    .withPocaFileId(pocaFileId)
                    .withPocaMailId(pocaMailId)
                    .withPocaEmail(senderEmail)
                    .withEmailSubject(emailSubject)
                    .build()
            ));
        } else {
            return apply(of(pocaEmailAlreadyReceived()
                    .withPocaFileId(pocaFileId)
                    .withPocaMailId(pocaMailId)
                    .withPocaEmail(senderEmail)
                    .withEmailSubject(emailSubject)
                    .build()
            ));
        }
    }
}
