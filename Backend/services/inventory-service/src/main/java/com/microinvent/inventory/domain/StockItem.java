package com.microinvent.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Một đơn vị hàng có serial (SRS 6.1.13). Serial duy nhất toàn hệ thống. */
@Entity
@Table(name = "stock_items")
@Getter
@Setter
public class StockItem {

    public enum Status { AVAILABLE, HOLD, IN_TRANSIT, RENTED, LOANED, SOLD, DAMAGED, RETURNED_TO_SUPPLIER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    /** NULL khi hàng không nằm trong kho nào (đang chuyển, đã bán, đang cho thuê). */
    @Column(name = "warehouse_id")
    private Integer warehouseId;

    /** Kho gần nhất – để tra cứu bảo hành khi hàng đã rời kho. */
    @Column(name = "last_warehouse_id")
    private Integer lastWarehouseId;

    @Column(name = "location_id")
    private Integer locationId;

    @Column(nullable = false, unique = true)
    private String serial;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.AVAILABLE;

    /** Giá vốn của đơn vị này, giữ nguyên khi chuyển kho. */
    @Column(name = "cost_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal costPrice = BigDecimal.ZERO;

    @Column(name = "supplier_id")
    private Integer supplierId;

    @Column(name = "receipt_id")
    private Integer receiptId;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    /** Chứng từ đang giữ / đang thuê / đang chuyển đơn vị này. */
    @Column(name = "current_ref_type", length = 30)
    private String currentRefType;

    @Column(name = "current_ref_id")
    private Integer currentRefId;

    @Version
    @Column(nullable = false)
    private Integer version;
}
