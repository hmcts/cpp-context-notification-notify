package uk.gov.moj.cpp.notification.notify.event.processor.task.handlers;

import static java.lang.Integer.parseInt;

import uk.gov.moj.cpp.notification.notify.event.processor.task.domain.MailServerCredentials;

import javax.mail.MessagingException;

public class EmailHandlerFactory {
    @SuppressWarnings("squid:S2095")
    public EmailHandler createEmailHandler(final MailServerCredentials mailServerCredentials) throws MessagingException {
        final EmailHandler bouncedEmailHandler = new EmailHandler();
        return (EmailHandler) getEmailHandler(mailServerCredentials, bouncedEmailHandler);
    }

    @SuppressWarnings("squid:S2095")
    public PocaEmailHandler createPocaEmailHandler(final MailServerCredentials mailServerCredentials) throws MessagingException {
        final PocaEmailHandler pocaEmailHandler = new PocaEmailHandler();
        return (PocaEmailHandler)  getEmailHandler(mailServerCredentials, pocaEmailHandler);
    }

    private BaseEmailHandler getEmailHandler(MailServerCredentials mailServerCredentials, BaseEmailHandler baseEmailHandler) throws MessagingException {
        baseEmailHandler.configureStore(mailServerCredentials.getUsername(), mailServerCredentials.getPassword(), mailServerCredentials.getServer(), parseInt(mailServerCredentials.getPort()));
        return baseEmailHandler;
    }
}
