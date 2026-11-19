package com.microinvent.catalog.domain;

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
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "products")
@Getter
@Setter
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private ProductGroup group;

    /** Mã sản phẩm, không đổi sau khi tạo (chứng từ lưu snapshot theo mã). */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false)
    private String name;

    /** Đơn vị tính: cái, bộ, mét... */
    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "default_price_in", precision = 18, scale = 2)
    private BigDecimal defaultPriceIn;

    /** Giá bán biểu kiến. */
    @Column(name = "default_price_out", precision = 18, scale = 2)
    private BigDecimal defaultPriceOut;

    @Column(name = "default_rent_price_per_day", precision = 18, scale = 2)
    private BigDecimal defaultRentPricePerDay;

    /** Ngưỡng tồn tối thiểu chung khi kho không đặt riêng. */
    @Column(name = "min_stock")
    private Integer minStock;

    @Column(name = "has_serial", nullable = false)
    private boolean hasSerial;

    @Column(name = "warranty_months")
    private Integer warrantyMonths;

    /** Đặt qua API upload ảnh (W4), không nhận trực tiếp từ client. */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Xóa mềm. */
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
