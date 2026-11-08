package com.microinvent.catalog.service;

import com.microinvent.catalog.domain.ProductGroup;
import com.microinvent.catalog.repository.ProductGroupRepository;
import com.microinvent.catalog.web.dto.ProductGroupRequest;
import com.microinvent.catalog.web.dto.ProductGroupResponse;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Nhóm sản phẩm (SRS 6.1.2) – service mẫu cho các module khác làm theo. */
@Service
@Transactional
public class ProductGroupService {

    private final ProductGroupRepository repository;

    public ProductGroupService(ProductGroupRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ProductGroupResponse> list(String keyword) {
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return repository.findAllWithProductCount(kw).stream()
                .map(v -> new ProductGroupResponse(v.getId(), v.getCode(), v.getName(), v.getDescription(),
                        v.getProductCount() == null ? 0 : v.getProductCount()))
                .toList();
    }

    public ProductGroupResponse create(ProductGroupRequest req) {
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Mã nhóm đã tồn tại");
        }
        if (repository.existsByNameIgnoreCaseAndDeletedFalse(req.name().trim())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Tên nhóm đã tồn tại");
        }
        ProductGroup g = new ProductGroup();
        g.setCode(code);
        apply(g, req);
        return toResponse(repository.save(g), 0);
    }

    public ProductGroupResponse update(Integer id, ProductGroupRequest req) {
        ProductGroup g = get(id);
        if (repository.existsByNameIgnoreCaseAndDeletedFalseAndIdNot(req.name().trim(), id)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Tên nhóm đã tồn tại");
        }
        // Mã nhóm không đổi sau khi tạo (đã dùng trong chứng từ/báo cáo).
        apply(g, req);
        return toResponse(g, repository.countActiveProducts(id));
    }

    /** Xóa mềm; chặn nếu nhóm còn sản phẩm. */
    public void delete(Integer id) {
        ProductGroup g = get(id);
        long products = repository.countActiveProducts(id);
        if (products > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "Nhóm còn " + products + " sản phẩm, không thể xóa");
        }
        g.setDeleted(true);
    }

    private ProductGroup get(Integer id) {
        return repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy nhóm sản phẩm"));
    }

    private static void apply(ProductGroup g, ProductGroupRequest req) {
        g.setName(req.name().trim());
        g.setDescription(StringUtils.hasText(req.description()) ? req.description().trim() : null);
    }

    private static ProductGroupResponse toResponse(ProductGroup g, long productCount) {
        return new ProductGroupResponse(g.getId(), g.getCode(), g.getName(), g.getDescription(), productCount);
    }
}
