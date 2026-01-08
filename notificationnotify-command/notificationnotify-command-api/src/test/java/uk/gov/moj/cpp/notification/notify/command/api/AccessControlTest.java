package uk.gov.moj.cpp.notification.notify.command.api;

import static java.util.Collections.singletonMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.accesscontrol.common.providers.UserAndGroupProvider;
import uk.gov.moj.cpp.accesscontrol.drools.Action;
import uk.gov.moj.cpp.accesscontrol.test.utils.BaseDroolsAccessControlTest;
import uk.gov.moj.cpp.notification.notify.command.api.accesscontrol.RuleConstants;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.kie.api.runtime.ExecutionResults;
import org.mockito.Mock;

@SuppressWarnings("Duplicates")
public class AccessControlTest extends BaseDroolsAccessControlTest {

    private static final String SEND_EMAIL_ACTION = "notificationnotify.send-email-notification";
    private static final String SEND_LETTER_ACTION = "notificationnotify.send-letter-notification";

    private Action action;

    @Mock
    private UserAndGroupProvider userAndGroupProvider;

    public AccessControlTest() {
        super("COMMAND_API_SESSION");
    }

    @Override
    protected Map<Class<?>, Object> getProviderMocks() {
        return singletonMap(UserAndGroupProvider.class, userAndGroupProvider);
    }

    @Test
    public void whenUserIsAMemberOfAllowedUserGroups_thenSuccessfullyAllowToSendEmail() {
        action = createActionFor(SEND_EMAIL_ACTION);
        when(userAndGroupProvider.isMemberOfAnyOfTheSuppliedGroups(action,
                RuleConstants.getSendEmailActionGroups())).thenReturn(true);

        final ExecutionResults executionResults = executeRulesWith(action);

        assertSuccessfulOutcome(executionResults);

        verify(userAndGroupProvider).isMemberOfAnyOfTheSuppliedGroups(action,
                RuleConstants.getSendEmailActionGroups());
        verifyNoMoreInteractions(userAndGroupProvider);
    }

    @Test
    public void whenUserIsNotAMemberOfAllowedUserGroups_thenFailSendEmail() {
        action = createActionFor(SEND_EMAIL_ACTION);
        when(userAndGroupProvider.isMemberOfAnyOfTheSuppliedGroups(action,
                RuleConstants.getSendEmailActionGroups())).thenReturn(false);

        final ExecutionResults executionResults = executeRulesWith(action);

        assertFailureOutcome(executionResults);

        verify(userAndGroupProvider).isMemberOfAnyOfTheSuppliedGroups(action,
                RuleConstants.getSendEmailActionGroups());
        verifyNoMoreInteractions(userAndGroupProvider);
    }

    @Test
    public void shouldGrantAccessForSendLetterIfMemberOfAllowedUserGroups() throws Exception {

        action = createActionFor(SEND_LETTER_ACTION);
        when(userAndGroupProvider.isMemberOfAnyOfTheSuppliedGroups(action,
                RuleConstants.getSendLetterActionGroups())).thenReturn(true);

        final ExecutionResults executionResults = executeRulesWith(action);

        assertSuccessfulOutcome(executionResults);

        verify(userAndGroupProvider).isMemberOfAnyOfTheSuppliedGroups(action,
                RuleConstants.getSendLetterActionGroups());
        verifyNoMoreInteractions(userAndGroupProvider);
    }

    @Test
    public void shouldDenyAccessForSendLetterIfNotMemberOfAllowedUserGroups() throws Exception {

        action = createActionFor(SEND_LETTER_ACTION);
        when(userAndGroupProvider.isMemberOfAnyOfTheSuppliedGroups(action,
                RuleConstants.getSendLetterActionGroups())).thenReturn(false);

        final ExecutionResults executionResults = executeRulesWith(action);

        assertFailureOutcome(executionResults);

        verify(userAndGroupProvider).isMemberOfAnyOfTheSuppliedGroups(action,
                RuleConstants.getSendLetterActionGroups());
        verifyNoMoreInteractions(userAndGroupProvider);
    }
}
