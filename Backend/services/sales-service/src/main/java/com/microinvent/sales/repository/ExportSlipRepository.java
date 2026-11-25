package com.microinvent.sales.repository;

import com.microinvent.sales.domain.ExportSlip;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ExportSlipRepository extends JpaRepository<ExportSlip, Integer> {

    /** Khóa phiếu khi sửa / tách / đổi trạng thái – 2 lần bấm "Xuất" cùng lúc chỉ 1 lần trừ tồn. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ExportSlip s WHERE s.id = :id")
    Optional<ExportSlip> lock(Integer id);

    /** Các phiếu được tách ra từ 1 phiếu gốc. */
    List<ExportSlip> findBySplitFromIdOrderById(Integer splitFromId);

    /**
     * Danh sách phiếu trong phạm vi kho được xem. {@code keyword} đã chuẩn hóa dạng "%abc%" chữ thường, ký tự
     * đại diện đã thoát bằng '!'. Lọc ngày theo ngày tạo phiếu.
     */
    @Query("""
            SELECT s FROM ExportSlip s
            WHERE (:allWarehouses = TRUE OR s.warehouseId IN :ids)
              AND (:customerId IS NULL OR s.customerId = :customerId)
              AND (:status IS NULL OR s.status = :status)
              AND (:exportType IS NULL OR s.exportType = :exportType)
              AND (:from IS NULL OR s.createdAt >= :from)
              AND (:to IS NULL OR s.createdAt < :to)
              AND (:keyword IS NULL OR LOWER(s.code) LIKE :keyword ESCAPE '!')
            """)
    Page<ExportSlip> search(boolean allWarehouses, Collection<Integer> ids, Integer customerId,
            ExportSlip.Status status, ExportSlip.ExportType exportType, LocalDateTime from, LocalDateTime to,
            String keyword, Pageable pageable);

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
