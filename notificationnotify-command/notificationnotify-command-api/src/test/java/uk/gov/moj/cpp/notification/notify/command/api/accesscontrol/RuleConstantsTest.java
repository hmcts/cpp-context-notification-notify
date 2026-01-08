package uk.gov.moj.cpp.notification.notify.command.api.accesscontrol;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

public class RuleConstantsTest {

    @Test
    public void shouldGetTheCorrectActionGroupsForSendEmail() throws Exception {

        final List<String> sendEmailActionGroups = RuleConstants.getSendEmailActionGroups();

        assertThat(sendEmailActionGroups.size(), is(8));
        assertThat(sendEmailActionGroups, hasItem("System Users"));
        assertThat(sendEmailActionGroups, hasItem("Online Plea System Users"));
        assertThat(sendEmailActionGroups, hasItem("Listing Officers"));
        assertThat(sendEmailActionGroups, hasItem("Court Clerks"));
        assertThat(sendEmailActionGroups, hasItem("Legal Advisers"));
        assertThat(sendEmailActionGroups, hasItem("Court Administrators"));
        assertThat(sendEmailActionGroups, hasItem("Court Associate"));
        assertThat(sendEmailActionGroups, hasItem("Crown Court Admin"));
    }

    @Test
    public void shouldGetTheCorrectActionGroupsForSendLetter() throws Exception {

        final List<String> sendEmailActionGroups = RuleConstants.getSendLetterActionGroups();

        assertThat(sendEmailActionGroups.size(), is(2));
        assertThat(sendEmailActionGroups, hasItem("System Users"));
        assertThat(sendEmailActionGroups, hasItem("Online Plea System Users"));
    }
}
