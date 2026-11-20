package com.microinvent.inventory.repository;

import com.microinvent.inventory.domain.Warehouse;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface WarehouseRepository extends JpaRepository<Warehouse, Integer> {

    boolean existsByCodeIgnoreCase(String code);

    /**
     * Lọc theo loại / trạng thái / từ khóa; {@code allWarehouses = false} thì chỉ trong {@code ids}
     * (kho được gán cho user).
     */
    @Query("""
            SELECT w FROM Warehouse w
            WHERE (:type IS NULL OR w.type = :type)
              AND (:status IS NULL OR w.status = :status)
              AND (:keyword IS NULL OR LOWER(w.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(w.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:allWarehouses = TRUE OR w.id IN :ids)
            """)
    Page<Warehouse> search(Warehouse.Type type, Warehouse.Status status, String keyword, boolean allWarehouses,
            Collection<Integer> ids, Pageable pageable);

    boolean existsByDefaultWarehouseTrue();

    /**
     * Khóa mọi dòng kho (SELECT … FOR UPDATE, theo thứ tự id) để các lần đổi kho mặc định chạy tuần tự –
     * tránh 2 request đồng thời làm 2 kho cùng is_default. Bảng kho chỉ vài chục dòng nên khóa cả bảng là đủ.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Warehouse w ORDER BY w.id")
    List<Warehouse> findAllForUpdate();

    /** Tổng tồn thực và giá trị tồn (theo giá vốn bình quân) của các kho. */
    @Query(value = """
            SELECT warehouse_id AS warehouseId, COALESCE(SUM(qty_on_hand), 0) AS totalQty,
                   COALESCE(SUM(qty_on_hand * avg_cost), 0) AS stockValue
            FROM stock_balances
            WHERE warehouse_id IN (:ids)
            GROUP BY warehouse_id
            """, nativeQuery = true)
    List<StockTotalView> sumStock(Collection<Integer> ids);

    // ---------- Điều kiện ngừng hoạt động kho (close-check) ----------

    @Query(value = "SELECT COALESCE(SUM(qty_on_hand), 0) FROM stock_balances WHERE warehouse_id = :id",
            nativeQuery = true)
    long sumOnHand(Integer id);

    @Query(value = "SELECT COALESCE(SUM(qty_rented_loaned), 0) FROM stock_balances WHERE warehouse_id = :id",
            nativeQuery = true)
    long sumRentedLoaned(Integer id);

    @Query(value = "SELECT COUNT(*) FROM stock_holds WHERE warehouse_id = :id AND status = 'ACTIVE'",
            nativeQuery = true)
    long countActiveHolds(Integer id);

    /** Phiếu chuyển đi/đến kho này đang IN_TRANSIT. */
    @Query(value = """
            SELECT COUNT(*) FROM stock_transfers
            WHERE (from_warehouse_id = :id OR to_warehouse_id = :id) AND status = 'IN_TRANSIT'
            """, nativeQuery = true)
    long countTransfersInTransit(Integer id);

    /** Phiếu chuyển chưa xong (trừ IN_TRANSIT đã đếm riêng) + phiếu kiểm kê chưa đóng. */
    @Query(value = """
            SELECT
              (SELECT COUNT(*) FROM stock_transfers
               WHERE (from_warehouse_id = :id OR to_warehouse_id = :id)
                 AND status IN ('DRAFT', 'PENDING', 'APPROVED', 'RECEIVED', 'DISCREPANCY'))
            + (SELECT COUNT(*) FROM stocktakes
               WHERE warehouse_id = :id AND status IN ('DRAFT', 'COUNTING', 'PENDING_APPROVAL'))
            """, nativeQuery = true)
    long countOpenDocuments(Integer id);

    interface StockTotalView {
        Integer getWarehouseId();

        Long getTotalQty();

        BigDecimal getStockValue();
    }
}
