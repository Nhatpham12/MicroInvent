package com.microinvent.catalog.domain;

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

/** Khách hàng (SRS 6.1.4). CCCD được mã hóa AES-256 ở tầng ứng dụng. */
@Entity
@Table(name = "customers")
@Getter
@Setter
public class Customer {

    public enum Type {
        RETAIL, BUSINESS
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Mã KH, không đổi sau khi tạo; bỏ trống khi tạo thì tự sinh KH00001... */
    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false)
    private Type customerType = Type.RETAIL;

    @Column(name = "tax_code", length = 20)
    private String taxCode;

    /** CCCD (khách bán lẻ bán lại hàng), đã mã hóa. */
    @Column(name = "id_card_no", columnDefinition = "VARBINARY(255)")
    private byte[] idCardNo;

    private String address;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
