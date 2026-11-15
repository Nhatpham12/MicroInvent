package com.microinvent.procurement.domain;

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

/** Phiếu nhập kho PNK (SRS 3.3). Kho nhận bắt buộc; xác nhận mới cộng tồn ở inventory-service. */
@Entity
@Table(name = "goods_receipts")
@Getter
@Setter
public class GoodsReceipt {

    public enum SourceType { SUPPLIER, RETAIL_CUSTOMER, OTHER }

    public enum Status { DRAFT, CONFIRMED, CANCELLED }

    public enum PaymentStatus { UNPAID, PARTIAL, PAID }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** PNK-yyyy-nnnn, sinh từ doc_sequences. */
    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "warehouse_id", nullable = false)
    private Integer warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType = SourceType.SUPPLIER;

    @Column(name = "supplier_id")
    private Integer supplierId;

    /** Khách lẻ bán lại hàng (source_type RETAIL_CUSTOMER). */
    @Column(name = "customer_id")
    private Integer customerId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "partner_snapshot")
    private Map<String, Object> partnerSnapshot;

    @Column(name = "receipt_date", nullable = false)
    private LocalDateTime receiptDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    @Column(name = "contract_no", length = 100)
    private String contractNo;

    @Column(name = "tracking_no", length = 100)
    private String trackingNo;

    @Column(name = "handover_doc_url", length = 500)
    private String handoverDocUrl;

    @Column(name = "bank_payment_ref", length = 100)
    private String bankPaymentRef;

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

    /** Thanh toán do sales/payment (Nhật) cập nhật; phiếu nhập chỉ đọc. */
    @Column(name = "paid_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_by", nullable = false)
    private Integer createdBy;

    @Column(name = "confirmed_by")
    private Integer confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<GoodsReceiptItem> items = new ArrayList<>();
}
