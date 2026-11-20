package com.microinvent.identity.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Gửi email OTP bất đồng bộ, chỉ sau khi transaction lưu OTP đã commit (BUG-04).
 * <ul>
 * <li>forgot-password trả về ngay, không chờ SMTP → thời gian phản hồi không lộ email có tồn tại hay không.</li>
 * <li>SMTP lỗi chỉ ghi log, API vẫn trả 200 như với email không tồn tại.</li>
 * <li>Transaction rollback thì không gửi mail cho OTP không có trong DB.</li>
 * </ul>
 */
@Component
public class OtpMailListener {

    private static final Logger log = LoggerFactory.getLogger(OtpMailListener.class);

    private final OtpMailSender mailSender;

    public OtpMailListener(OtpMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOtpRequested(OtpMailRequested event) {
        try {
            mailSender.sendOtp(event.email(), event.fullName(), event.otp(), event.ttlMinutes());
        } catch (RuntimeException ex) {
            log.error("Không gửi được email OTP cho user {}", event.userId(), ex);
        }
    }
}
