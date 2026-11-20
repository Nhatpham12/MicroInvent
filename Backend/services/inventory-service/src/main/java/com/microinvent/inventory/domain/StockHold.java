package com.microinvent.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Lượt giữ hàng tại kho cho khách / chứng từ (SRS 3.4.2). Hàng có serial: mỗi serial 1 dòng, qty = 1. */
@Entity
@Table(name = "stock_holds")
@Getter
@Setter
public class StockHold {

    public enum RefType { MANUAL, EXPORT_SLIP, TRANSFER, RENTAL }

    public enum Status { ACTIVE, RELEASED, CONSUMED, EXPIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Column(name = "warehouse_id", nullable = false)
    private Integer warehouseId;

    @Column(name = "stock_item_id")
    private Long stockItemId;

    @Column(nullable = false)
    private int qty;

    @Column(name = "customer_id")
    private Integer customerId;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "held_by_user_id", nullable = false)
    private Integer heldByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ref_type", nullable = false)
    private RefType refType = RefType.MANUAL;

    @Column(name = "ref_id")
    private Integer refId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
