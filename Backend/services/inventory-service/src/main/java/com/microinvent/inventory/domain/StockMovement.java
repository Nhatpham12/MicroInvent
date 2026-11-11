package com.microinvent.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** Nhật ký biến động tồn thực (SRS 6.1.16) – chỉ thêm, không sửa. */
@Entity
@Table(name = "stock_movements")
@Getter
@Setter
public class StockMovement {

    public enum Type { IMPORT, EXPORT, RENT, LOAN, RETURN, TRANSFER_OUT, TRANSFER_IN, ADJUST, DAMAGED, WARRANTY, HOLD, RELEASE }

    public enum RefType { RECEIPT, EXPORT_SLIP, RENTAL, RENTAL_RETURN, SALES_RETURN, TRANSFER, STOCKTAKE, HOLD, MANUAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Column(name = "warehouse_id", nullable = false)
    private Integer warehouseId;

    @Column(name = "stock_item_id")
    private Long stockItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false)
    private Type movementType;

    @Enumerated(EnumType.STRING)
    @Column(name = "ref_type", nullable = false)
    private RefType refType;

    @Column(name = "ref_id", nullable = false)
    private Integer refId;

    /** + nhập, − xuất (theo tồn thực qty_on_hand). */
    @Column(name = "qty_change", nullable = false)
    private int qtyChange;

    @Column(name = "qty_before", nullable = false)
    private int qtyBefore;

    @Column(name = "qty_after", nullable = false)
    private int qtyAfter;

    @Column(name = "unit_cost", precision = 18, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
