package uk.gov.moj.cpp.notification.notify.event.processor.sender;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.SEND_EMAIL;
import static uk.gov.moj.cpp.notification.notify.event.processor.task.Task.SEND_LETTER;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SenderFactoryTest {

    @Mock
    private EmailSender emailSender;

    @Mock
    private LetterSender letterSender;

    @InjectMocks
    private SenderFactory senderFactory;

    @Test
    public void shouldGetTheCorrectSenderForAnEmail() throws Exception {

        assertThat(senderFactory.senderFor(SEND_EMAIL), is(emailSender));
    }

    @Test
    public void shouldGetTheCorrectSenderForALetter() throws Exception {

        assertThat(senderFactory.senderFor(SEND_LETTER), is(letterSender));
    }
}
