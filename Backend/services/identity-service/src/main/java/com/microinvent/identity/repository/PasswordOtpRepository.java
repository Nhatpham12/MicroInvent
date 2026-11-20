package com.microinvent.identity.repository;

import com.microinvent.identity.domain.PasswordOtp;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PasswordOtpRepository extends JpaRepository<PasswordOtp, Long> {

    Optional<PasswordOtp> findFirstByUserIdOrderByCreatedAtDescIdDesc(Integer userId);

    /**
     * OTP mới nhất của user, khóa dòng (SELECT … FOR UPDATE) để các lần verify-otp song song chạy tuần tự:
     * mỗi lần nhập sai đều được cộng vào attempts, không bị ghi đè lẫn nhau (BUG-02).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordOtp> findFirstForUpdateByUserIdOrderByCreatedAtDescIdDesc(Integer userId);

    /**
     * Tìm theo reset token và khóa dòng: request đến sau phải chờ request trước commit, lúc đó token đã bị xóa
     * nên không tìm thấy nữa – reset token chỉ dùng được 1 lần kể cả khi gửi song song (BUG-03).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordOtp> findForUpdateByResetTokenHash(String resetTokenHash);

    /** Vô hiệu các OTP chưa dùng của user (khi gửi OTP mới). */
    @Modifying
    @Query("update PasswordOtp o set o.usedAt = :now where o.userId = :userId and o.usedAt is null")
    int invalidateAll(Integer userId, LocalDateTime now);
}
