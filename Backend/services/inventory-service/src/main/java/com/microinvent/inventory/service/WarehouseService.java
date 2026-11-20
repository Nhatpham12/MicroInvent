package com.microinvent.inventory.service;

import com.microinvent.common.context.UserContext;
import com.microinvent.common.context.UserContextHolder;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.common.web.PageResponse;
import com.microinvent.inventory.domain.ProductWarehouseSetting;
import com.microinvent.inventory.domain.Warehouse;
import com.microinvent.inventory.repository.ProductWarehouseSettingRepository;
import com.microinvent.inventory.repository.WarehouseRepository;
import com.microinvent.inventory.repository.WarehouseRepository.StockTotalView;
import com.microinvent.inventory.web.dto.CloseCheckResponse;
import com.microinvent.inventory.web.dto.CloseCheckResponse.Blocker;
import com.microinvent.inventory.web.dto.CloseCheckResponse.BlockerType;
import com.microinvent.inventory.web.dto.MinStockRequest;
import com.microinvent.inventory.web.dto.MinStockResponse;
import com.microinvent.inventory.web.dto.WarehouseRequest;
import com.microinvent.inventory.web.dto.WarehouseResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Danh mục kho, trạng thái kho, kho mặc định, ngưỡng tồn theo kho (SRS 3.10.1, 3.10.4). */
@Service
@Transactional
public class WarehouseService {

    private static final Logger log = LoggerFactory.getLogger(WarehouseService.class);

    /** Trường được phép dùng trong ?sort= của danh sách kho (totalQty, stockValue là số tính thêm, không sắp xếp được). */
    static final Set<String> SORTABLE_FIELDS = Set.of("code", "name", "type", "status", "createdAt");

    private final WarehouseRepository repository;
    private final ProductWarehouseSettingRepository settings;

    public WarehouseService(WarehouseRepository repository, ProductWarehouseSettingRepository settings) {
        this.repository = repository;
        this.settings = settings;
    }

    /** Vai trò không có all_warehouses (thủ kho, sales) chỉ thấy kho được gán. */
    @Transactional(readOnly = true)
    public PageResponse<WarehouseResponse> list(Warehouse.Type type, Warehouse.Status status, String keyword,
            Pageable pageable) {
        checkSort(pageable.getSort());
        UserContext user = UserContextHolder.get();
        Set<Integer> ids = user.warehouseIds().stream().map(Long::intValue).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            ids = Set.of(-1); // tránh "IN ()" rỗng
        }
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Page<Warehouse> page = repository.search(type, status, kw, user.allWarehouses(), ids, pageable);

        List<Integer> pageIds = page.getContent().stream().map(Warehouse::getId).toList();
        Map<Integer, StockTotalView> totals = pageIds.isEmpty() ? Map.of()
                : repository.sumStock(pageIds).stream()
                        .collect(Collectors.toMap(StockTotalView::getWarehouseId, Function.identity()));
        return PageResponse.of(page.map(w -> toResponse(w, totals.get(w.getId()))));
    }

    @Transactional(readOnly = true)
    public WarehouseResponse detail(Integer id) {
        checkAccess(id);
        return toResponse(get(id));
    }

    public WarehouseResponse create(WarehouseRequest req) {
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Mã kho đã tồn tại");
        }
        Warehouse w = new Warehouse();
        w.setCode(code);
        apply(w, req);
        // Chưa có kho mặc định (thường là kho đầu tiên) thì kho mới tự nhận, để hệ thống luôn có kho mặc định.
        w.setDefaultWarehouse(!repository.existsByDefaultWarehouseTrue());
        return toResponse(repository.save(w), null);
    }

    public WarehouseResponse update(Integer id, WarehouseRequest req) {
        Warehouse w = get(id);
        // Mã kho không đổi sau khi tạo (đã in trên chứng từ, dùng trong báo cáo).
        apply(w, req);
        return toResponse(w);
    }

    @Transactional(readOnly = true)
    public CloseCheckResponse closeCheck(Integer id) {
        get(id);
        List<Blocker> blockers = new ArrayList<>();
        add(blockers, BlockerType.STOCK_REMAINING, repository.sumOnHand(id), "Kho còn %d đơn vị tồn");
        // Đếm theo phiếu: hàng đang đến kho (qty_in_transit_in) luôn thuộc một phiếu IN_TRANSIT đã được đếm ở đây.
        add(blockers, BlockerType.IN_TRANSIT, repository.countTransfersInTransit(id),
                "Còn %d phiếu chuyển kho đang vận chuyển đi hoặc đến kho");
        add(blockers, BlockerType.OPEN_DOCUMENTS, repository.countOpenDocuments(id),
                "Còn %d phiếu chuyển kho / kiểm kê chưa hoàn tất");
        add(blockers, BlockerType.ACTIVE_HOLDS, repository.countActiveHolds(id), "Còn %d lượt giữ hàng");
        add(blockers, BlockerType.ACTIVE_RENTALS, repository.sumRentedLoaned(id),
                "Còn %d đơn vị đang cho thuê / cho mượn chưa trả");
        return new CloseCheckResponse(blockers.isEmpty(), blockers);
    }

    /** Chỉ được CLOSED khi close-check không còn điều kiện chặn (SRS 8.5#8); kho mặc định không được ngừng. */
    public WarehouseResponse changeStatus(Integer id, Warehouse.Status status, String reason) {
        Warehouse w = get(id);
        if (status != Warehouse.Status.ACTIVE && w.isDefaultWarehouse()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Không thể ngừng kho mặc định, hãy đặt kho khác làm mặc định trước");
        }
        if (status == Warehouse.Status.CLOSED) {
            CloseCheckResponse check = closeCheck(id);
            if (!check.canClose()) {
                throw new BusinessException(ErrorCode.WAREHOUSE_CANNOT_CLOSE,
                        "Kho chưa đủ điều kiện ngừng hoạt động", check.blockers());
            }
        }
        UserContext user = UserContextHolder.get();
        // TODO(audit): gửi audit.log sang report-service khi có audit_logs; tạm thời chỉ ghi log.
        log.info("Đổi trạng thái kho {} (id={}): {} -> {} bởi {} (id={}), lý do: {}", w.getCode(), id,
                w.getStatus(), status, user.username(), user.userId(), StringUtils.hasText(reason) ? reason : "-");
        w.setStatus(status);
        return toResponse(w);
    }

    public WarehouseResponse setDefault(Integer id) {
        List<Warehouse> all = repository.findAllForUpdate();
        Warehouse w = all.stream().filter(x -> x.getId().equals(id)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy kho"));
        if (w.getStatus() != Warehouse.Status.ACTIVE) {
            throw new BusinessException(ErrorCode.CONFLICT, "Chỉ kho đang hoạt động mới được đặt làm mặc định");
        }
        all.forEach(x -> x.setDefaultWarehouse(x == w));
        return toResponse(w);
    }

    @Transactional(readOnly = true)
    public List<MinStockResponse> listMinStock(Integer warehouseId) {
        checkAccess(warehouseId);
        get(warehouseId);
        return settings.findByWarehouseIdOrderByProductId(warehouseId).stream()
                .map(s -> new MinStockResponse(s.getProductId(), s.getWarehouseId(), s.getMinStock()))
                .toList();
    }

    public List<MinStockResponse> updateMinStock(Integer warehouseId, MinStockRequest req) {
        checkAccess(warehouseId);
        get(warehouseId);
        for (MinStockRequest.Item item : req.items()) {
            ProductWarehouseSetting.Key key = new ProductWarehouseSetting.Key(item.productId(), warehouseId);
            if (item.minStock() == null) {
                settings.deleteById(key);
            } else {
                settings.save(new ProductWarehouseSetting(item.productId(), warehouseId, item.minStock()));
            }
        }
        return listMinStock(warehouseId);
    }

    private Warehouse get(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy kho"));
    }

    /** Kiểm tra quyền trên kho ở server (SRS 3.10.3, 8.3) – theo id kho trong đường dẫn. */
    private static void checkAccess(Integer warehouseId) {
        if (!UserContextHolder.get().canAccessWarehouse(warehouseId.longValue())) {
            throw new BusinessException(ErrorCode.WAREHOUSE_FORBIDDEN);
        }
    }

    /** Trường sort lạ làm Hibernate lỗi khi dựng query (500) – chặn trước, trả 400. */
    private static void checkSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                String allowed = String.join(", ", SORTABLE_FIELDS.stream().sorted().toList());
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(),
                        Map.of("sort", "Chỉ sắp xếp được theo: " + allowed));
            }
        }
    }

    private static void add(List<Blocker> blockers, BlockerType type, long count, String message) {
        if (count > 0) {
            blockers.add(new Blocker(type, count, String.format(message, count)));
        }
    }

    private static void apply(Warehouse w, WarehouseRequest req) {
        w.setName(req.name().trim());
        w.setType(req.type());
        w.setAddress(req.address().trim());
        w.setProvince(trimToNull(req.province()));
        w.setPhone(trimToNull(req.phone()));
        w.setEmail(trimToNull(req.email()));
        w.setManagerUserId(req.managerUserId());
        w.setColor(trimToNull(req.color()));
        w.setNote(trimToNull(req.note()));
    }

    private static String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private WarehouseResponse toResponse(Warehouse w) {
        List<StockTotalView> totals = repository.sumStock(List.of(w.getId()));
        return toResponse(w, totals.isEmpty() ? null : totals.get(0));
    }

    private static WarehouseResponse toResponse(Warehouse w, StockTotalView total) {
        return total == null ? WarehouseResponse.of(w, 0, BigDecimal.ZERO)
                : WarehouseResponse.of(w, total.getTotalQty(), total.getStockValue());
    }
}
