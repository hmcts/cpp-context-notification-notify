package uk.gov.moj.cpp.notification.notify.query.api.rule;


import static java.util.Collections.singletonMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.accesscontrol.common.providers.UserAndGroupProvider;
import uk.gov.moj.cpp.accesscontrol.drools.Action;
import uk.gov.moj.cpp.accesscontrol.test.utils.BaseDroolsAccessControlTest;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kie.api.runtime.ExecutionResults;
import org.mockito.Mock;

public class NotificationQueryAPIAccessControlTest extends BaseDroolsAccessControlTest {

    private static final String NOTIFICATIONNOTIFY_QUERY_NOTIFICATION = "notificationnotify.query.notification";

    private static final String NOTIFICATIONNOTIFY_QUERY_FIND_NOTIFICATION = "notificationnotify.query.find-notification";

    private static final Boolean SYSTEM_USER = true;

    private Action action;

    @Mock
    private UserAndGroupProvider userAndGroupProvider;

    public NotificationQueryAPIAccessControlTest() {
        super("QUERY_API_SESSION");
    }

    @Override
    protected Map<Class<?>, Object> getProviderMocks() {
        return singletonMap(UserAndGroupProvider.class, userAndGroupProvider);
    }

    @AfterEach
    public void tearDown() throws Exception {
        verifyNoMoreInteractions(userAndGroupProvider);
    }

    @Test
    public void shouldPassAccessControlForGetNotificationWhenUserIsSystemUser() throws Exception {
        action = createActionFor(NOTIFICATIONNOTIFY_QUERY_NOTIFICATION);

        mockUsersGroupsResponse(SYSTEM_USER);

        final ExecutionResults executionResults = executeRulesWith(action);

        assertSuccessfulOutcome(executionResults);

        verify(userAndGroupProvider).isSystemUser(action);
    }

    @Test
    public void shouldFailAccessControlForGetNotificationWhenUserIsSystemUser() throws Exception {
        action = createActionFor(NOTIFICATIONNOTIFY_QUERY_NOTIFICATION);

        mockUsersGroupsResponse(!SYSTEM_USER);

        final ExecutionResults executionResults = executeRulesWith(action);

        assertFailureOutcome(executionResults);

        verify(userAndGroupProvider).isSystemUser(action);
    }

    @Test
    public void shouldPassAccessControlForFindNotificationsWhenUserIsSystemUser() throws Exception {
        action = createActionFor(NOTIFICATIONNOTIFY_QUERY_FIND_NOTIFICATION);

        mockUsersGroupsResponse(SYSTEM_USER);

        final ExecutionResults executionResults = executeRulesWith(action);

        assertSuccessfulOutcome(executionResults);

        verify(userAndGroupProvider).isSystemUser(action);
    }

    @Test
    public void shouldFailAccessControlForFindNotificationsWhenUserIsSystemUser() throws Exception {
        action = createActionFor(NOTIFICATIONNOTIFY_QUERY_FIND_NOTIFICATION);

        mockUsersGroupsResponse(!SYSTEM_USER);

        final ExecutionResults executionResults = executeRulesWith(action);

        assertFailureOutcome(executionResults);

        verify(userAndGroupProvider).isSystemUser(action);
    }

    private void mockUsersGroupsResponse(final boolean isSystemUser) {
        when(userAndGroupProvider.isSystemUser(action)).thenReturn(isSystemUser);
    }
}
