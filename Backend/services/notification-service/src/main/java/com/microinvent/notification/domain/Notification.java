package com.microinvent.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Cảnh báo gửi tới một người dùng (SRS 3.9). Mỗi người nhận một dòng để đánh dấu đã đọc riêng. */
@Entity
@Table(name = "notifications")
@Getter
@Setter
public class Notification {

    /** Trùng với ENUM ở cột notifications.type. */
    public enum Type {
        LOW_STOCK, RENT_DUE, LOAN_DUE, RENT_OVERDUE, APPROVAL_PENDING, TRANSFER_OVERDUE, TRANSFER_DISCREPANCY,
        HOLD_EXPIRING, DEBT_OVERDUE, SYNC_CONFLICT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "warehouse_id")
    private Integer warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    /** Loại chứng từ liên quan (RECEIPT, EXPORT, TRANSFER, RENTAL, PRODUCT...) để FE mở đúng màn. */
    @Column(name = "ref_type", length = 30)
    private String refType;

    @Column(name = "ref_id")
    private Integer refId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "is_resolved", nullable = false)
    private boolean resolved;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
