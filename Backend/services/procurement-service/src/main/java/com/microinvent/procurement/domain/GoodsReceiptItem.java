package com.microinvent.procurement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Dòng phiếu nhập; snapshot mã / tên / ĐVT sản phẩm lúc lập phiếu (chứng từ in ra không đổi khi danh mục đổi). */
@Entity
@Table(name = "goods_receipt_items")
@Getter
@Setter
public class GoodsReceiptItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false)
    private GoodsReceipt receipt;

    /** Id sản phẩm ở catalog-service. */
    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "product_snapshot", nullable = false)
    private Map<String, Object> productSnapshot;

    @Column(nullable = false)
    private int qty;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal lineTotal;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> serials;

    @Column(name = "condition_note")
    private String conditionNote;
}
