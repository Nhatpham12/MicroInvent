package com.microinvent.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ngưỡng tồn tối thiểu của 1 sản phẩm tại 1 kho (SRS 3.10.4); không có dòng = dùng products.min_stock. */
@Entity
@Table(name = "product_warehouse_settings")
@IdClass(ProductWarehouseSetting.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class ProductWarehouseSetting {

    /** Id sản phẩm ở catalog-service. */
    @Id
    @Column(name = "product_id")
    private Integer productId;

    @Id
    @Column(name = "warehouse_id")
    private Integer warehouseId;

    @Column(name = "min_stock", nullable = false)
    private int minStock;

    public ProductWarehouseSetting(Integer productId, Integer warehouseId, int minStock) {
        this.productId = productId;
        this.warehouseId = warehouseId;
        this.minStock = minStock;
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private Integer productId;
        private Integer warehouseId;
    }
}
