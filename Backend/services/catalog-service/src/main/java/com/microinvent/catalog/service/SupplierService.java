package com.microinvent.catalog.service;

import com.microinvent.catalog.domain.Supplier;
import com.microinvent.catalog.repository.SupplierRepository;
import com.microinvent.catalog.security.FieldEncryptor;
import com.microinvent.catalog.web.dto.SupplierRequest;
import com.microinvent.catalog.web.dto.SupplierResponse;
import com.microinvent.common.context.UserContext;
import com.microinvent.common.context.UserContextHolder;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.common.web.PageResponse;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Nhà cung cấp (SRS 3.3.2): tìm kiếm / sắp xếp / phân trang, tạo, sửa, xóa mềm. Số tài khoản lưu mã hóa
 * AES-256-GCM; danh sách luôn che số, màn chi tiết hiện đủ cho ADMIN/MANAGER/ACCOUNTANT.
 */
@Service
@Transactional
public class SupplierService {

    static final Set<String> REVEAL_ROLES = Set.of("ADMIN", "MANAGER", "ACCOUNTANT");

    private final SupplierRepository repository;
    private final FieldEncryptor encryptor;
    private final CatalogAudit audit;

    public SupplierService(SupplierRepository repository, FieldEncryptor encryptor, CatalogAudit audit) {
        this.repository = repository;
        this.encryptor = encryptor;
        this.audit = audit;
    }

    /** @param keyword tìm theo mã, tên, mã số thuế, SĐT */
    public record Filter(String keyword) {
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierResponse> search(Filter filter, Pageable pageable) {
        return PageResponse.of(repository.findAll(spec(filter), pageable).map(s -> toResponse(s, false)));
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(Integer id) {
        return toResponse(find(id), true);
    }

    public SupplierResponse create(SupplierRequest req) {
        String code = Texts.upperCode(req.code());
        if (code != null && repository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Mã nhà cung cấp đã tồn tại");
        }
        String taxCode = req.taxCode().trim();
        if (repository.existsByTaxCode(taxCode)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Mã số thuế đã được dùng cho nhà cung cấp khác");
        }
        Supplier s = new Supplier();
        s.setCode(code != null ? code : PartnerCodes.temporary());
        apply(s, req, true);
        Supplier saved = repository.saveAndFlush(s);
        if (code == null) {
            saved.setCode(PartnerCodes.fromId("NCC", saved.getId(), repository::existsByCodeIgnoreCase));
        }
        audit.record("CREATE", "SUPPLIER", saved.getId(), null, snapshot(saved));
        return toResponse(saved, true);
    }

    public SupplierResponse update(Integer id, SupplierRequest req) {
        Supplier s = find(id);
        if (repository.existsByTaxCodeAndIdNot(req.taxCode().trim(), id)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Mã số thuế đã được dùng cho nhà cung cấp khác");
        }
        Map<String, Object> before = snapshot(s);
        apply(s, req, false);
        audit.record("UPDATE", "SUPPLIER", id, before, snapshot(s));
        return toResponse(s, true);
    }

    public void delete(Integer id) {
        Supplier s = find(id);
        s.setDeleted(true);
        audit.record("DELETE", "SUPPLIER", id, snapshot(s), null);
    }

    /** Trường ghi nhật ký – không có STK / CCCD. */
    private static Map<String, Object> snapshot(Supplier s) {
        return CatalogAudit.snapshot("code", s.getCode(), "name", s.getName(), "taxCode", s.getTaxCode(),
                "phoneFax", s.getPhoneFax());
    }

    private Supplier find(Integer id) {
        return repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy nhà cung cấp"));
    }

    private void apply(Supplier s, SupplierRequest req, boolean creating) {
        s.setName(req.name().trim());
        s.setTaxCode(req.taxCode().trim());
        s.setAddress(Texts.trimToNull(req.address()));
        s.setPhoneFax(Texts.trimToNull(req.phoneFax()));
        s.setEmail(Texts.trimToNull(req.email()));
        s.setBankAccountName(Texts.trimToNull(req.bankAccountName()));
        s.setBankName(Texts.trimToNull(req.bankName()));
        s.setNote(Texts.trimToNull(req.note()));
        if (creating || req.bankAccountNo() != null) {
            String account = Texts.digitsOnly(req.bankAccountNo());
            if (account != null && !account.matches("\\d{6,20}")) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số tài khoản phải có 6–20 chữ số");
            }
            s.setBankAccountNo(encryptor.encrypt(account));
        }
    }

    static Specification<Supplier> spec(Filter f) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.isFalse(root.get("deleted")));
            if (StringUtils.hasText(f.keyword())) {
                String like = Texts.likePattern(f.keyword());
                where.add(cb.or(cb.like(cb.lower(root.get("code")), like, '!'),
                        cb.like(cb.lower(root.get("name")), like, '!'),
                        cb.like(root.get("taxCode"), like, '!'),
                        cb.like(root.get("phoneFax"), like, '!')));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private SupplierResponse toResponse(Supplier s, boolean detail) {
        String account = encryptor.decrypt(s.getBankAccountNo());
        boolean reveal = detail && canReveal(UserContextHolder.get());
        boolean masked = account != null && !reveal;
        return new SupplierResponse(s.getId(), s.getCode(), s.getName(), s.getTaxCode(), s.getAddress(),
                s.getPhoneFax(), s.getEmail(), masked ? FieldEncryptor.mask(account) : account, masked,
                s.getBankAccountName(), s.getBankName(), s.getNote(), s.getCreatedAt(), s.getUpdatedAt());
    }

    static boolean canReveal(UserContext user) {
        return user.roles().stream().anyMatch(REVEAL_ROLES::contains);
    }
}
