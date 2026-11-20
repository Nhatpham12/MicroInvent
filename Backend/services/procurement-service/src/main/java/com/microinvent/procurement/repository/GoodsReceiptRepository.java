package com.microinvent.procurement.repository;

import com.microinvent.procurement.domain.GoodsReceipt;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Integer> {

    /** Khóa phiếu khi đổi trạng thái – 2 lần bấm "Xác nhận" cùng lúc chỉ 1 lần cộng tồn. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM GoodsReceipt r WHERE r.id = :id")
    Optional<GoodsReceipt> lock(Integer id);

    @Query("""
            SELECT r FROM GoodsReceipt r
            WHERE (:allWarehouses = TRUE OR r.warehouseId IN :ids)
              AND (:supplierId IS NULL OR r.supplierId = :supplierId)
              AND (:status IS NULL OR r.status = :status)
              AND (:from IS NULL OR r.receiptDate >= :from)
              AND (:to IS NULL OR r.receiptDate < :to)
              AND (:keyword IS NULL OR LOWER(r.code) LIKE :keyword ESCAPE '!'
                   OR LOWER(r.contractNo) LIKE :keyword ESCAPE '!')
            """)
    Page<GoodsReceipt> search(boolean allWarehouses, Collection<Integer> ids, Integer supplierId,
            GoodsReceipt.Status status, LocalDateTime from, LocalDateTime to, String keyword, Pageable pageable);

    /** Tăng số thứ tự chứng từ theo (prefix, năm) và khóa dòng tới hết transaction – an toàn khi tạo đồng thời. */
    @Modifying
    @Query(value = """
            INSERT INTO doc_sequences (prefix, year, last_no) VALUES (:prefix, :year, 1)
            ON DUPLICATE KEY UPDATE last_no = last_no + 1
            """, nativeQuery = true)
    int nextSequence(String prefix, int year);

    @Query(value = "SELECT last_no FROM doc_sequences WHERE prefix = :prefix AND year = :year", nativeQuery = true)
    int currentSequence(String prefix, int year);
}
