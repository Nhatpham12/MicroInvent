package com.microinvent.sales.domain;

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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Dòng phiếu xuất; snapshot sản phẩm lúc lập phiếu, giá vốn và hạn bảo hành ghi lúc xuất. */
@Entity
@Table(name = "export_slip_items")
@Getter
@Setter
public class ExportSlipItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slip_id", nullable = false)
    private ExportSlip slip;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "product_snapshot", nullable = false)
    private Map<String, Object> productSnapshot;

    @Column(nullable = false)
    private int qty;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    /** Giá vốn lúc xuất (inventory trả về khi commit) – tính lợi nhuận. */
    @Column(name = "unit_cost", precision = 18, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "line_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal lineTotal;

    /** Serial xuất (sản phẩm có serial: bắt buộc, đúng bằng số lượng). */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> serials;

    @Column(name = "warranty_months")
    private Integer warrantyMonths;

    /** Ngày xuất + số tháng bảo hành (ghi lúc xuất). */
    @Column(name = "warranty_until")
    private LocalDate warrantyUntil;
}
