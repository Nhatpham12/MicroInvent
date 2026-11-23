package com.microinvent.sales.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Phiếu xuất kho PXK (SRS 3.4). Kho xuất bắt buộc. Tạo → giữ hàng tại kho xuất (thiếu hàng thì vẫn lưu, chưa giữ,
 * chờ tách theo kho) → xác nhận → xuất (trừ tồn thực ở inventory-service, phát export.exported).
 */
@Entity
@Table(name = "export_slips")
@Getter
@Setter
public class ExportSlip {

    public enum ExportType { SALE, DAMAGED, WARRANTY, RETURN_SUPPLIER, OTHER }

    /** PENDING = chờ xác nhận; chỉ sửa / tách / hủy được khi chưa EXPORTED. */
    public enum Status { PENDING, CONFIRMED, EXPORTED, CANCELLED }

    public enum DeliveryMethod { PICKUP, CARRIER }

    public enum PaymentStatus { UNPAID, PARTIAL, PAID }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** PXK-yyyy-nnnn, sinh từ doc_sequences. */
    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "export_type", nullable = false)
    private ExportType exportType = ExportType.SALE;

    @Column(name = "warehouse_id", nullable = false)
    private Integer warehouseId;

    /** Id khách ở catalog-service (bắt buộc với phiếu bán). */
    @Column(name = "customer_id")
    private Integer customerId;

    /** Mã, tên, địa chỉ, MST, SĐT khách lúc lập phiếu. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "customer_snapshot")
    private Map<String, Object> customerSnapshot;

    /** Thời điểm xuất thực tế (lúc chuyển EXPORTED). */
    @Column(name = "export_date")
    private LocalDateTime exportDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    /** Đã giữ đủ hàng ở kho xuất chưa (thiếu hàng → false, chờ tách phiếu). */
    @Column(name = "stock_reserved", nullable = false)
    private boolean stockReserved;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method")
    private DeliveryMethod deliveryMethod;

    @Column(name = "tracking_no", length = 100)
    private String trackingNo;

    /** Cột ENUM của MySQL ('NONE','0','5'…) – JDBC báo là CHAR nên khai báo CHAR cho Hibernate validate khớp. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "vat_rate", nullable = false)
    private VatRate vatRate = VatRate.NONE;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "vat_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal vatAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "deposit_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal depositAmount = BigDecimal.ZERO;

    /** Thanh toán / công nợ do phần payment (Nhật – T34) cập nhật. */
    @Column(name = "paid_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    /** Hạn thanh toán (công nợ). */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /** Nhân viên bán (báo cáo doanh thu theo nhân viên). */
    @Column(name = "sales_user_id")
    private Integer salesUserId;

    /** Phiếu gốc nếu phiếu này được tách theo kho từ phiếu khác. */
    @Column(name = "split_from_id")
    private Integer splitFromId;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_by", nullable = false)
    private Integer createdBy;

    @Column(name = "confirmed_by")
    private Integer confirmedBy;

    @Column(name = "exported_by")
    private Integer exportedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "slip", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ExportSlipItem> items = new ArrayList<>();
}
