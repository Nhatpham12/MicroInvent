package com.microinvent.inventory.repository;

import com.microinvent.inventory.domain.StockBalance;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface StockBalanceRepository extends JpaRepository<StockBalance, Long> {

    Optional<StockBalance> findByProductIdAndWarehouseId(Integer productId, Integer warehouseId);

    /** Đọc tồn của (sản phẩm, kho) và khóa dòng tới hết transaction (SELECT … FOR UPDATE). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM StockBalance b WHERE b.productId = :productId AND b.warehouseId = :warehouseId")
    Optional<StockBalance> lock(Integer productId, Integer warehouseId);

    /**
     * Tạo dòng tồn rỗng nếu chưa có, đồng thời khóa X dòng đó. Không dùng INSERT IGNORE: gặp dòng trùng nó chỉ
     * lấy khóa S, rồi lock() xin khóa X → 2 request đồng thời cùng nâng S→X sẽ deadlock (MySQL 1213).
     */
    @Modifying
    @Query(value = """
            INSERT INTO stock_balances (product_id, warehouse_id) VALUES (:productId, :warehouseId)
            ON DUPLICATE KEY UPDATE version = version
            """, nativeQuery = true)
    int insertIfAbsent(Integer productId, Integer warehouseId);

    /** Tồn của 1 sản phẩm ở các kho đang hoạt động khác (gợi ý kho khác khi thiếu hàng). */
    @Query("""
            SELECT b FROM StockBalance b, Warehouse w
            WHERE w.id = b.warehouseId AND w.status = com.microinvent.inventory.domain.Warehouse.Status.ACTIVE
              AND b.productId = :productId AND b.warehouseId <> :excludeWarehouseId
              AND b.qtyOnHand - b.qtyHold - b.qtyDamaged > 0
            ORDER BY b.warehouseId
            """)
    List<StockBalance> findAvailableElsewhere(Integer productId, Integer excludeWarehouseId);
}
