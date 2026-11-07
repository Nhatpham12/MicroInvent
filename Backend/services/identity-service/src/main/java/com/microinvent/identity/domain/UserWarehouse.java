package com.microinvent.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Kho được gán cho người dùng (vai trò không all_warehouses chỉ thao tác được trên các kho này). */
@Entity
@Table(name = "user_warehouses")
@Getter
@Setter
public class UserWarehouse {

    @EmbeddedId
    private Id id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** FALSE = chỉ xem. */
    @Column(name = "can_write", nullable = false)
    private boolean canWrite = true;

    public Integer getWarehouseId() {
        return id.getWarehouseId();
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {

        @Column(name = "user_id")
        private Integer userId;

        @Column(name = "warehouse_id")
        private Integer warehouseId;
    }
}
