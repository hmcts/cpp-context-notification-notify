package uk.gov.moj.cpp.notification.notify.paas;

import static java.lang.System.getenv;

public class EnvironmentHelper {

    public static final String DISABLE_SEND_EMAIL = "DISABLE_SEND_EMAIL";
    public static final String TEST_RECIPIENT_EMAIL = "TEST_RECIPIENT_EMAIL";
    public static final String MAIL_SMTP_SSL_PROTOCOLS = "mail.smtp.ssl.protocols";

    private EnvironmentHelper() {
    }

    public static String getEnvironmentVariableForSmtpSettings() {
        return getenv().getOrDefault("SMTP_SETTINGS", "{\"outlookCredentials\": [{\"serverId\": \"1\", \"server\": \"smtp.example.com\", \"port\": \"587\", \"username\": \"SMTP-USER-1\", \"password\": \"SMTP-PASSWORD-1\"}, {\"serverId\": \"2\", \"server\": \"smtp.example.com\", \"port\": \"587\", \"username\": \"SMTP-USER-2\", \"password\": \"SMTP-PASSWORD-2\"}, {\"serverId\": \"3\", \"server\": \"smtp.example.com\", \"port\": \"587\", \"username\": \"SMTP-USER-3\", \"password\": \"SMTP-PASSWORD-3\"} ] }");
    }

    public static String getEnvironmentVariableForRetrySettings() {
        return getenv().getOrDefault("RETRY_SETTINGS", "{\"retryDefinitions\":[{\"retryNumber\":1,\"duration\":\"1\"}, {\"retryNumber\":2,\"duration\":\"2\"},{\"retryNumber\":3,\"duration\":\"3\"}]}");
    }

    public static String getVaultUrl() {
        return getenv().get("VAULT_URL");
    }

    public static String getEnvironmentVariableForDisableSendEmail() {
        return getenv().getOrDefault(DISABLE_SEND_EMAIL, "false");
    }

    public static String getEnvironmentVariableForTestRecipientEmail() {
        return getenv().getOrDefault(TEST_RECIPIENT_EMAIL, "");
    }

    public static String getEnvironmentVariableForMailSmtpSslProtocols() {
        return getenv().getOrDefault(MAIL_SMTP_SSL_PROTOCOLS, "TLSv1.2");
    }

    public static String getVariable(final String variableName) {
        return getenv(variableName);
    }
}