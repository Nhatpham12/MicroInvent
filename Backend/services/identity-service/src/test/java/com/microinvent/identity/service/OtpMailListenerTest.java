package com.microinvent.identity.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;

class OtpMailListenerTest {

    private final OtpMailSender sender = mock(OtpMailSender.class);
    private final OtpMailListener listener = new OtpMailListener(sender);

    @Test
    void sendsOtpMail() {
        listener.onOtpRequested(new OtpMailRequested(7, "a@x.vn", "A", "123456", 5));
        verify(sender).sendOtp("a@x.vn", "A", "123456", 5);
    }

    @Test
    void smtpFailureIsOnlyLogged() {
        doThrow(new MailSendException("down")).when(sender).sendOtp("a@x.vn", "A", "123456", 5);
        assertThatCode(() -> listener.onOtpRequested(new OtpMailRequested(7, "a@x.vn", "A", "123456", 5)))
                .doesNotThrowAnyException();
    }
}
