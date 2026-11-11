package com.microinvent.inventory.repository;

import com.microinvent.inventory.domain.StockItem;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface StockItemRepository extends JpaRepository<StockItem, Long> {

    /** Serial đã có trong hệ thống (ở bất kỳ kho / trạng thái nào). */
    Optional<StockItem> findBySerial(String serial);

    /** Đơn vị có serial đang nằm trong các kho được xem; lọc sản phẩm / trạng thái / một phần serial. */
    @Query("""
            SELECT i FROM StockItem i
            WHERE (:allWarehouses = TRUE OR i.warehouseId IN :ids OR i.lastWarehouseId IN :ids)
              AND (:productId IS NULL OR i.productId = :productId)
              AND (:status IS NULL OR i.status = :status)
              AND (:keyword IS NULL OR LOWER(i.serial) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<StockItem> search(boolean allWarehouses, Collection<Integer> ids, Integer productId, StockItem.Status status,
            String keyword, Pageable pageable);

    @Query("SELECT i.serial FROM StockItem i WHERE i.serial IN :serials")
    List<String> findExistingSerials(Collection<String> serials);

    /** Khóa các đơn vị theo serial (thứ tự serial tăng dần) – 2 phiếu giữ cùng serial chỉ 1 phiếu thành công. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM StockItem i WHERE i.serial IN :serials ORDER BY i.serial")
    List<StockItem> lockBySerials(Collection<String> serials);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM StockItem i WHERE i.id IN :ids ORDER BY i.id")
    List<StockItem> lockByIds(Collection<Long> ids);
}
