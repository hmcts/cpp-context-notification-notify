package uk.gov.moj.cpp.notification.notify.command.api.accesscontrol;

import static java.util.Arrays.asList;

import java.util.List;

public class RuleConstants {

    private static final String GROUP_SYSTEM_USERS = "System Users";
    private static final String GROUP_ONLINE_PLEA_SYSTEM_USERS = "Online Plea System Users";
    private static final String LISTING_OFFICERS = "Listing Officers";
    private static final String COURT_CLERKS = "Court Clerks";
    private static final String LEGAL_ADVISERS = "Legal Advisers";
    private static final String COURT_ADMINISTRATORS = "Court Administrators";
    private static final String COURT_ASSOCIATE = "Court Associate";
    private static final String CROWN_COURT_ADMIN = "Crown Court Admin";


    private RuleConstants() {
        throw new IllegalAccessError("Utility class");
    }

    public static List<String> getSendEmailActionGroups() {
        return asList(GROUP_SYSTEM_USERS, GROUP_ONLINE_PLEA_SYSTEM_USERS, LISTING_OFFICERS, COURT_CLERKS, LEGAL_ADVISERS, COURT_ADMINISTRATORS, COURT_ASSOCIATE, CROWN_COURT_ADMIN);
    }

    public static List<String> getSendLetterActionGroups() {
        return asList(GROUP_SYSTEM_USERS, GROUP_ONLINE_PLEA_SYSTEM_USERS);
    }
}
