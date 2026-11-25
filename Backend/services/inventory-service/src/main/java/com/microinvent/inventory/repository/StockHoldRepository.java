package com.microinvent.inventory.repository;

import com.microinvent.inventory.domain.StockHold;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface StockHoldRepository extends JpaRepository<StockHold, Long> {

    boolean existsByStockItemIdIn(Collection<Long> stockItemIds);

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

    /** Khóa 1 lượt giữ (nhả / gia hạn / hết hạn không chạy chồng lên nhau). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM StockHold h WHERE h.id = :id")
    Optional<StockHold> lockById(Long id);

    /** Danh sách giữ hàng trong phạm vi kho được xem ({@code allWarehouses = false} → chỉ {@code ids}). */
    @Query("""
            SELECT h FROM StockHold h
            WHERE (:allWarehouses = TRUE OR h.warehouseId IN :ids)
              AND (:productId IS NULL OR h.productId = :productId)
              AND (:customerId IS NULL OR h.customerId = :customerId)
              AND (:status IS NULL OR h.status = :status)
              AND (:refType IS NULL OR h.refType = :refType)
            """)
    Page<StockHold> search(boolean allWarehouses, Collection<Integer> ids, Integer productId, Integer customerId,
            StockHold.Status status, StockHold.RefType refType, Pageable pageable);

    /** Id các lượt giữ thủ công còn hiệu lực đã quá hạn (HoldExpiryJob tự nhả; giữ cho chứng từ không tự hết hạn). */
    @Query("""
            SELECT h.id FROM StockHold h
            WHERE h.status = com.microinvent.inventory.domain.StockHold.Status.ACTIVE
              AND h.refType = com.microinvent.inventory.domain.StockHold.RefType.MANUAL
              AND h.expiresAt IS NOT NULL AND h.expiresAt <= :now
            ORDER BY h.expiresAt, h.id
            """)
    List<Long> findExpiredIds(LocalDateTime now, Pageable pageable);
}
