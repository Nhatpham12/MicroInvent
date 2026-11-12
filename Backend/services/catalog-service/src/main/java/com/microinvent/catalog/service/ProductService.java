package com.microinvent.catalog.service;

import com.microinvent.catalog.domain.Product;
import com.microinvent.catalog.domain.ProductGroup;
import com.microinvent.catalog.repository.ProductGroupRepository;
import com.microinvent.catalog.repository.ProductRepository;
import com.microinvent.catalog.web.dto.ProductRequest;
import com.microinvent.catalog.web.dto.ProductResponse;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.common.web.PageResponse;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Sản phẩm (SRS 3.2.1): tìm kiếm / lọc / sắp xếp / phân trang, tạo, sửa, xóa mềm.
 * Mã sản phẩm không đổi sau khi tạo và là duy nhất kể cả với sản phẩm đã xóa (UNIQUE ở DB).
 */
@Service
@Transactional
public class ProductService {

    private final ProductRepository products;
    private final ProductGroupRepository groups;

    public ProductService(ProductRepository products, ProductGroupRepository groups) {
        this.products = products;
        this.groups = groups;
    }

    /**
     * @param keyword tìm theo mã hoặc tên (không phân biệt hoa thường)
     * @param minPrice / maxPrice lọc theo giá bán (defaultPriceOut)
     */
    public record Filter(String keyword, Integer groupId, BigDecimal minPrice, BigDecimal maxPrice,
            Boolean hasSerial) {
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(Filter filter, Pageable pageable) {
        if (filter.minPrice() != null && filter.maxPrice() != null
                && filter.minPrice().compareTo(filter.maxPrice()) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Giá từ phải nhỏ hơn hoặc bằng giá đến");
        }
        return PageResponse.of(products.findAll(spec(filter), pageable).map(ProductService::toResponse));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Integer id) {
        return toResponse(find(id));
    }

    public ProductResponse create(ProductRequest req) {
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        if (products.existsByCodeIgnoreCase(code)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Mã sản phẩm đã tồn tại");
        }
        Product p = new Product();
        p.setCode(code);
        apply(p, req);
        return toResponse(products.save(p));
    }

    public ProductResponse update(Integer id, ProductRequest req) {
        Product p = find(id);
        apply(p, req);
        return toResponse(p);
    }

    public void delete(Integer id) {
        find(id).setDeleted(true);
    }

    private Product find(Integer id) {
        return products.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy sản phẩm"));
    }

    private void apply(Product p, ProductRequest req) {
        ProductGroup group = groups.findByIdAndDeletedFalse(req.groupId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "Nhóm sản phẩm không tồn tại"));
        p.setGroup(group);
        p.setName(req.name().trim());
        p.setUnit(req.unit().trim());
        p.setDefaultPriceIn(req.defaultPriceIn());
        p.setDefaultPriceOut(req.defaultPriceOut());
        p.setDefaultRentPricePerDay(req.defaultRentPricePerDay());
        p.setMinStock(req.minStock());
        p.setHasSerial(Boolean.TRUE.equals(req.hasSerial()));
        p.setWarrantyMonths(req.warrantyMonths());
        p.setDescription(StringUtils.hasText(req.description()) ? req.description().trim() : null);
    }

    /** Ghép điều kiện lọc; chỉ thêm điều kiện cho tham số khác null. */
    static Specification<Product> spec(Filter f) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.isFalse(root.get("deleted")));
            if (StringUtils.hasText(f.keyword())) {
                String like = "%" + escapeLike(f.keyword().trim().toLowerCase(Locale.ROOT)) + "%";
                where.add(cb.or(cb.like(cb.lower(root.get("code")), like, '!'),
                        cb.like(cb.lower(root.get("name")), like, '!')));
            }
            if (f.groupId() != null) {
                where.add(cb.equal(root.get("group").get("id"), f.groupId()));
            }
            if (f.minPrice() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("defaultPriceOut"), f.minPrice()));
            }
            if (f.maxPrice() != null) {
                where.add(cb.lessThanOrEqualTo(root.get("defaultPriceOut"), f.maxPrice()));
            }
            if (f.hasSerial() != null) {
                where.add(cb.equal(root.get("hasSerial"), f.hasSerial()));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    /** Thoát ký tự đại diện của LIKE (ký tự thoát là !) để tìm đúng chuỗi người dùng gõ. */
    static String escapeLike(String s) {
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    static ProductResponse toResponse(Product p) {
        ProductGroup g = p.getGroup();
        return new ProductResponse(p.getId(), p.getCode(), p.getName(), g.getId(), g.getCode(), g.getName(),
                p.getUnit(), p.getDefaultPriceIn(), p.getDefaultPriceOut(), p.getDefaultRentPricePerDay(),
                p.getMinStock(), p.isHasSerial(), p.getWarrantyMonths(), p.getImageUrl(), p.getDescription(),
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
