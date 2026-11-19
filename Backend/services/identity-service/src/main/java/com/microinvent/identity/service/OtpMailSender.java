package com.microinvent.identity.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Gửi mã OTP quên mật khẩu qua email (dev: Mailpit, xem thư tại http://localhost:8025). */
@Component
public class OtpMailSender {

    private final JavaMailSender mailSender;
    private final String from;

    public OtpMailSender(JavaMailSender mailSender, @Value("${microinvent.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void sendOtp(String to, String fullName, String otp, long ttlMinutes) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject("[MicroInvent] Mã xác thực đặt lại mật khẩu");
        msg.setText("Xin chào " + fullName + ",\n\n"
                + "Mã OTP để đặt lại mật khẩu của bạn là: " + otp + "\n"
                + "Mã có hiệu lực trong " + ttlMinutes + " phút. Không chia sẻ mã này cho bất kỳ ai.\n\n"
                + "Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này.");
        mailSender.send(msg);
    }
}
