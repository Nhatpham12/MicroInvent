package com.microinvent.inventory.repository;

import com.microinvent.inventory.domain.StockMovement;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    /** Nhật ký biến động trong các kho được xem; khoảng thời gian [from, to). */
    @Query("""
            SELECT m FROM StockMovement m
            WHERE (:allWarehouses = TRUE OR m.warehouseId IN :ids)
              AND (:productId IS NULL OR m.productId = :productId)
              AND (:type IS NULL OR m.movementType = :type)
              AND (:from IS NULL OR m.createdAt >= :from)
              AND (:to IS NULL OR m.createdAt < :to)
            """)
    Page<StockMovement> search(boolean allWarehouses, Collection<Integer> ids, Integer productId,
            StockMovement.Type type, LocalDateTime from, LocalDateTime to, Pageable pageable);

    /** Biến động của 1 sản phẩm theo 1 chứng từ (dựng lịch sử của 1 serial: phiếu nhập, phiếu xuất…). */
    List<StockMovement> findByProductIdAndRefTypeAndRefIdOrderById(Integer productId, StockMovement.RefType refType,
            Integer refId);
}
