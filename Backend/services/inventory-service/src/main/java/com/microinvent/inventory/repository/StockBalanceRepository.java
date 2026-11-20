package com.microinvent.inventory.repository;

import com.microinvent.inventory.domain.StockBalance;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /** Tồn theo (sản phẩm, kho) trong phạm vi kho được xem ({@code allWarehouses = false} → chỉ {@code ids}). */
    @Query("""
            SELECT b FROM StockBalance b
            WHERE (:allWarehouses = TRUE OR b.warehouseId IN :ids)
              AND (:productId IS NULL OR b.productId = :productId)
              AND (:hasStock IS NULL OR (:hasStock = TRUE AND b.qtyOnHand > 0) OR (:hasStock = FALSE AND b.qtyOnHand = 0))
            """)
    Page<StockBalance> search(boolean allWarehouses, Collection<Integer> ids, Integer productId, Boolean hasStock,
            Pageable pageable);

    @Query("""
            SELECT b FROM StockBalance b
            WHERE b.productId = :productId AND (:allWarehouses = TRUE OR b.warehouseId IN :ids)
            ORDER BY b.warehouseId
            """)
    List<StockBalance> findByProductInScope(Integer productId, boolean allWarehouses, Collection<Integer> ids);

    /**
     * Sản phẩm dưới ngưỡng riêng theo kho (product_warehouse_settings). Có ngưỡng mà chưa từng nhập → khả dụng 0.
     * Ngưỡng chung products.min_stock ở catalog-service – chưa tính (cần Feign).
     */
    @Query(value = """
            SELECT s.product_id AS productId, s.warehouse_id AS warehouseId, s.min_stock AS minStock,
                   COALESCE(b.qty_on_hand - b.qty_hold - b.qty_damaged, 0) AS available
            FROM product_warehouse_settings s
            LEFT JOIN stock_balances b ON b.product_id = s.product_id AND b.warehouse_id = s.warehouse_id
            WHERE COALESCE(b.qty_on_hand - b.qty_hold - b.qty_damaged, 0) < s.min_stock
              AND (:allWarehouses = TRUE OR s.warehouse_id IN (:ids))
            ORDER BY s.warehouse_id, s.product_id
            """, nativeQuery = true)
    List<BelowMinRow> findBelowMin(boolean allWarehouses, Collection<Integer> ids);

    interface BelowMinRow {
        Integer getProductId();

        Integer getWarehouseId();

        Integer getMinStock();

        Integer getAvailable();
    }

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
