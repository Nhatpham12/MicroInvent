package com.microinvent.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Nhà cung cấp (SRS 3.3.2). Số tài khoản được mã hóa AES-256 ở tầng ứng dụng. */
@Entity
@Table(name = "suppliers")
@Getter
@Setter
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Mã NCC, không đổi sau khi tạo; bỏ trống khi tạo thì tự sinh NCC00001... */
    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false)
    private String name;

    /** Mã số thuế, duy nhất kể cả NCC đã xóa mềm. */
    @Column(name = "tax_code", nullable = false, unique = true, length = 20)
    private String taxCode;

    private String address;

    @Column(name = "phone_fax", length = 50)
    private String phoneFax;

    @Column(length = 100)
    private String email;

    /** Đã mã hóa (xem FieldEncryptor). */
    @Column(name = "bank_account_no", columnDefinition = "VARBINARY(255)")
    private byte[] bankAccountNo;

    @Column(name = "bank_account_name", length = 100)
    private String bankAccountName;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
