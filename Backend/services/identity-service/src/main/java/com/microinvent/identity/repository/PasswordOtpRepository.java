package com.microinvent.identity.repository;

import com.microinvent.identity.domain.PasswordOtp;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PasswordOtpRepository extends JpaRepository<PasswordOtp, Long> {

    Optional<PasswordOtp> findFirstByUserIdOrderByCreatedAtDescIdDesc(Integer userId);

    Optional<PasswordOtp> findByResetTokenHash(String resetTokenHash);

    /** Vô hiệu các OTP chưa dùng của user (khi gửi OTP mới). */
    @Modifying
    @Query("update PasswordOtp o set o.usedAt = :now where o.userId = :userId and o.usedAt is null")
    int invalidateAll(Integer userId, LocalDateTime now);
}
