package com.microinvent.inventory.repository;

import com.microinvent.inventory.domain.StockHold;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface StockHoldRepository extends JpaRepository<StockHold, Long> {

    /** Đọc không khóa – chỉ để biết cần khóa dòng tồn nào trước (giữ thứ tự khóa: tồn → serial → hold). */
    List<StockHold> findByRefTypeAndRefIdAndStatus(StockHold.RefType refType, Integer refId, StockHold.Status status);

    /** Hold đang hiệu lực của 1 chứng từ, khóa dòng (commit / release không chạy chồng lên nhau). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT h FROM StockHold h
            WHERE h.refType = :refType AND h.refId = :refId
              AND h.status = com.microinvent.inventory.domain.StockHold.Status.ACTIVE
            ORDER BY h.productId, h.id
            """)
    List<StockHold> lockActiveByRef(StockHold.RefType refType, Integer refId);
}
