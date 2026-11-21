package com.microinvent.catalog.service;

import com.microinvent.catalog.domain.Customer;
import com.microinvent.catalog.repository.CustomerRepository;
import com.microinvent.catalog.security.FieldEncryptor;
import com.microinvent.catalog.web.dto.CustomerRequest;
import com.microinvent.catalog.web.dto.CustomerResponse;
import com.microinvent.common.context.UserContextHolder;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.common.web.PageResponse;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Khách hàng (SRS 6.1.4): tìm theo tên / SĐT / MST, tạo, sửa, xóa mềm. CCCD lưu mã hóa AES-256-GCM;
 * danh sách luôn che số, màn chi tiết hiện đủ cho ADMIN/MANAGER/ACCOUNTANT.
 */
@Service
@Transactional
public class CustomerService {

    private final CustomerRepository repository;
    private final FieldEncryptor encryptor;

    public CustomerService(CustomerRepository repository, FieldEncryptor encryptor) {
        this.repository = repository;
        this.encryptor = encryptor;
    }

    /** @param keyword tìm theo mã, tên, SĐT, mã số thuế */
    public record Filter(String keyword, Customer.Type customerType) {
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> search(Filter filter, Pageable pageable) {
        return PageResponse.of(repository.findAll(spec(filter), pageable).map(c -> toResponse(c, false)));
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Integer id) {
        return toResponse(find(id), true);
    }

    public CustomerResponse create(CustomerRequest req) {
        String code = Texts.upperCode(req.code());
        if (code != null && repository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Mã khách hàng đã tồn tại");
        }
        String taxCode = Texts.trimToNull(req.taxCode());
        if (taxCode != null && repository.existsByTaxCodeAndDeletedFalse(taxCode)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Mã số thuế đã được dùng cho khách hàng khác");
        }
        Customer c = new Customer();
        c.setCode(code != null ? code : PartnerCodes.temporary());
        apply(c, req, true);
        Customer saved = repository.saveAndFlush(c);
        if (code == null) {
            saved.setCode(PartnerCodes.fromId("KH", saved.getId(), repository::existsByCodeIgnoreCase));
        }
        return toResponse(saved, true);
    }

    public CustomerResponse update(Integer id, CustomerRequest req) {
        Customer c = find(id);
        String taxCode = Texts.trimToNull(req.taxCode());
        if (taxCode != null && repository.existsByTaxCodeAndDeletedFalseAndIdNot(taxCode, id)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Mã số thuế đã được dùng cho khách hàng khác");
        }
        apply(c, req, false);
        return toResponse(c, true);
    }

    public void delete(Integer id) {
        find(id).setDeleted(true);
    }

    private Customer find(Integer id) {
        return repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy khách hàng"));
    }

    private void apply(Customer c, CustomerRequest req, boolean creating) {
        Customer.Type type = req.customerType() == null ? Customer.Type.RETAIL : req.customerType();
        String taxCode = Texts.trimToNull(req.taxCode());
        if (type == Customer.Type.BUSINESS && taxCode == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Khách doanh nghiệp phải có mã số thuế");
        }
        c.setName(req.name().trim());
        c.setCustomerType(type);
        c.setTaxCode(taxCode);
        c.setAddress(Texts.trimToNull(req.address()));
        c.setPhone(Texts.trimToNull(req.phone()));
        c.setEmail(Texts.trimToNull(req.email()));
        c.setNote(Texts.trimToNull(req.note()));
        if (creating || req.idCardNo() != null) {
            String idCard = Texts.digitsOnly(req.idCardNo());
            if (idCard != null && !idCard.matches("\\d{9}|\\d{12}")) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "CCCD gồm 12 số (CMND cũ 9 số)");
            }
            c.setIdCardNo(encryptor.encrypt(idCard));
        }
    }

    static Specification<Customer> spec(Filter f) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.isFalse(root.get("deleted")));
            if (StringUtils.hasText(f.keyword())) {
                String like = Texts.likePattern(f.keyword());
                where.add(cb.or(cb.like(cb.lower(root.get("code")), like, '!'),
                        cb.like(cb.lower(root.get("name")), like, '!'),
                        cb.like(root.get("phone"), like, '!'),
                        cb.like(root.get("taxCode"), like, '!')));
            }
            if (f.customerType() != null) {
                where.add(cb.equal(root.get("customerType"), f.customerType()));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private CustomerResponse toResponse(Customer c, boolean detail) {
        String idCard = encryptor.decrypt(c.getIdCardNo());
        boolean reveal = detail && SupplierService.canReveal(UserContextHolder.get());
        boolean masked = idCard != null && !reveal;
        return new CustomerResponse(c.getId(), c.getCode(), c.getName(), c.getCustomerType(), c.getTaxCode(),
                masked ? FieldEncryptor.mask(idCard) : idCard, masked, c.getAddress(), c.getPhone(), c.getEmail(),
                c.getNote(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
