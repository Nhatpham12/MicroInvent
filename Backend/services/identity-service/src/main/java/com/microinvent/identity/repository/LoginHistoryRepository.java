package com.microinvent.identity.repository;

import com.microinvent.identity.domain.LoginHistory;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {

    /** Thống kê đăng nhập theo tháng trong khoảng [from, to). */
    @Query(value = """
            SELECT MONTH(created_at) AS month,
                   SUM(CASE WHEN success THEN 1 ELSE 0 END) AS successCount,
                   SUM(CASE WHEN success THEN 0 ELSE 1 END) AS failedCount,
                   COUNT(DISTINCT CASE WHEN success THEN user_id END) AS activeUsers
            FROM login_histories
            WHERE created_at >= :from AND created_at < :to
            GROUP BY MONTH(created_at)
            """, nativeQuery = true)
    List<MonthlyAccess> countByMonth(LocalDateTime from, LocalDateTime to);

    interface MonthlyAccess {
        Integer getMonth();

        Long getSuccessCount();

        Long getFailedCount();

        Long getActiveUsers();
    }
}
