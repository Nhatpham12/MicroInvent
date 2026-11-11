package com.microinvent.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Tồn theo cặp (sản phẩm, kho) – SRS 3.10.4. Mọi thay đổi đi qua InternalStockService, đọc bằng
 * {@code SELECT … FOR UPDATE} (StockBalanceRepository#lock) trong cùng transaction.
 */
@Entity
@Table(name = "stock_balances")
@Getter
@Setter
public class StockBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Id sản phẩm ở catalog-service. */
    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Column(name = "warehouse_id", nullable = false)
    private Integer warehouseId;

    /** Tồn thực, gồm cả hàng đang giữ và hàng hỏng. */
    @Column(name = "qty_on_hand", nullable = false)
    private int qtyOnHand;

    @Column(name = "qty_hold", nullable = false)
    private int qtyHold;

    @Column(name = "qty_damaged", nullable = false)
    private int qtyDamaged;

    @Column(name = "qty_in_transit_in", nullable = false)
    private int qtyInTransitIn;

    @Column(name = "qty_rented_loaned", nullable = false)
    private int qtyRentedLoaned;

    /** Giá vốn bình quân gia quyền. */
    @Column(name = "avg_cost", nullable = false, precision = 18, scale = 2)
    private BigDecimal avgCost = BigDecimal.ZERO;

    @Version
    @Column(nullable = false)
    private Integer version;

    /** MySQL tự cập nhật (ON UPDATE CURRENT_TIMESTAMP) – chỉ đọc. */
    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    /** Khả dụng = tồn thực − giữ − hỏng (SRS 3.10.4). */
    public int available() {
        return qtyOnHand - qtyHold - qtyDamaged;
    }
}
