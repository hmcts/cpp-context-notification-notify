package uk.gov.moj.cpp.notification.notify.query.api;

import static org.mockito.Mockito.verify;
import static uk.gov.justice.services.core.annotation.Component.QUERY_API;
import static uk.gov.justice.services.test.utils.core.matchers.HandlerMatcher.isHandler;
import static uk.gov.justice.services.test.utils.core.matchers.HandlerMethodMatcher.method;

import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.notification.notify.query.view.NotificationQueryView;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class NotificationQueryApiTest {

    @InjectMocks
    private NotificationQueryApi notificationQueryApi;

    @Mock
    private JsonEnvelope query;

    @Mock
    private NotificationQueryView notificationQueryView;

    @Test
    public void shouldVerifyGetNotificationByIdIsPassedThroughToTheQueryView() {
        notificationQueryApi.getNotificationById(query);
        verify(notificationQueryView).getNotificationById(query);
        MatcherAssert.assertThat(notificationQueryApi,
                isHandler(QUERY_API)
                        .with(method("getNotificationById")
                                .thatHandles("notificationnotify.query.notification")
                        ));
    }

    @Test
    public void shouldVerifyFindNotificationIsPassedThroughToTheQueryView() {
        notificationQueryApi.findNotification(query);
        verify(notificationQueryView).findNotification(query);
        MatcherAssert.assertThat(notificationQueryApi,
                isHandler(QUERY_API)
                        .with(method("findNotification")
                                .thatHandles("notificationnotify.query.find-notification")
                        ));
    }

}