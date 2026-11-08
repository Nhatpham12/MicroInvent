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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Danh mục kho đa địa điểm (SRS 3.10.1, 6.1.9). */
@Entity
@Table(name = "warehouses")
@Getter
@Setter
public class Warehouse {

    public enum Type { MAIN, BRANCH, WARRANTY, DAMAGED }

    public enum Status { ACTIVE, SUSPENDED, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** vd: HN01, HCM01 – không đổi sau khi tạo. */
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(nullable = false)
    private String address;

    @Column(length = 100)
    private String province;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    /** Thủ kho phụ trách – id user ở identity-service. */
    @Column(name = "manager_user_id")
    private Integer managerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    /** Chỉ 1 kho TRUE, đảm bảo ở WarehouseService. */
    @Column(name = "is_default", nullable = false)
    private boolean defaultWarehouse;

    /** Màu nhận diện trên header FE, vd #1677FF. */
    @Column(length = 7)
    private String color;

    /** Khóa giao dịch khi kiểm kê. */
    @Column(name = "is_stocktake_locked", nullable = false)
    private boolean stocktakeLocked;

    @Column(columnDefinition = "TEXT")
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
