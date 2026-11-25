package com.microinvent.inventory.service;

import com.microinvent.common.context.UserContext;
import com.microinvent.common.context.UserContextHolder;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.common.web.PageResponse;
import com.microinvent.inventory.domain.StockBalance;
import com.microinvent.inventory.domain.StockHold;
import com.microinvent.inventory.domain.StockItem;
import com.microinvent.inventory.domain.StockMovement;
import com.microinvent.inventory.domain.Warehouse;
import com.microinvent.inventory.repository.StockBalanceRepository;
import com.microinvent.inventory.repository.StockHoldRepository;
import com.microinvent.inventory.repository.StockItemRepository;
import com.microinvent.inventory.repository.StockMovementRepository;
import com.microinvent.inventory.repository.WarehouseRepository;
import com.microinvent.inventory.web.dto.DamagedRequest;
import com.microinvent.inventory.web.dto.HoldExtendRequest;
import com.microinvent.inventory.web.dto.HoldRequest;
import com.microinvent.inventory.web.dto.HoldView;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Giữ hàng thủ công cho khách (SRS 3.4.2): tạo, gia hạn, nhả, tự nhả khi hết hạn; đánh dấu hàng hỏng (SRS 2.3.6).
 * Giữ hàng cho chứng từ (phiếu xuất, thuê…) đi qua API nội bộ reserve / commit / release, không sửa ở đây.
 * Thứ tự khóa giống InternalStockService: dòng tồn → serial → lượt giữ.
 */
@Service
@Transactional
public class HoldService {

    private final WarehouseRepository warehouses;
    private final StockBalanceRepository balances;
    private final StockItemRepository items;
    private final StockHoldRepository holds;
    private final StockMovementRepository movements;
    private final Clock clock;
    private final int defaultDays;

    public HoldService(WarehouseRepository warehouses, StockBalanceRepository balances, StockItemRepository items,
            StockHoldRepository holds, StockMovementRepository movements, Clock clock,
            @Value("${microinvent.hold.default-days:3}") int defaultDays) {
        this.warehouses = warehouses;
        this.balances = balances;
        this.items = items;
        this.holds = holds;
        this.movements = movements;
        this.clock = clock;
        this.defaultDays = defaultDays;
    }

    // ------------------------------------------------------------------ tra cứu

    @Transactional(readOnly = true)
    public PageResponse<HoldView> list(Integer warehouseId, Integer productId, Integer customerId,
            StockHold.Status status, StockHold.RefType refType, Pageable pageable) {
        UserContext user = UserContextHolder.get();
        boolean all;
        Set<Integer> ids;
        if (warehouseId != null) {
            checkAccess(warehouseId);
            all = false;
            ids = Set.of(warehouseId);
        } else {
            all = user.allWarehouses();
            ids = user.warehouseIds().stream().map(Long::intValue).collect(Collectors.toSet());
            if (ids.isEmpty()) {
                ids = Set.of(-1); // tránh "IN ()" rỗng
            }
        }
        Map<Integer, String> names = warehouseNames();
        var page = holds.search(all, ids, productId, customerId, status, refType, pageable);
        Map<Long, String> serials = serialsOf(page.getContent());
        return PageResponse.of(page.map(h -> HoldView.of(h, names.get(h.getWarehouseId()),
                serials.get(h.getStockItemId()))));
    }

    // ------------------------------------------------------------------ giữ / gia hạn / nhả

    /**
     * Giữ hàng tại kho cho khách: trừ tồn khả dụng ngay. Sản phẩm quản lý serial phải chỉ đúng serial (mỗi serial
     * 1 lượt giữ). Không đủ khả dụng → 409 STOCK_NOT_ENOUGH; serial không có sẵn ở kho → 409.
     */
    public List<HoldView> create(HoldRequest req) {
        checkAccess(req.warehouseId());
        Warehouse w = requireWritable(req.warehouseId());
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expiresAt = req.expiresAt() != null ? req.expiresAt() : now.plusDays(defaultDays);
        if (!expiresAt.isAfter(now)) {
            throw invalid("expiresAt", "Hạn giữ phải sau thời điểm hiện tại");
        }
        List<String> serials = checkSerials(req.productId(), req.qty(), req.serials());

        StockBalance b = balances.lock(req.productId(), req.warehouseId()).orElse(null);
        int available = b == null ? 0 : b.available();
        if (b == null || available < req.qty()) {
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH, ErrorCode.STOCK_NOT_ENOUGH.defaultMessage(),
                    List.of(Map.of("productId", req.productId(), "requested", req.qty(), "available", available)));
        }
        List<StockItem> units = lockAvailableSerials(req.productId(), req.warehouseId(), serials);

        b.setQtyHold(b.getQtyHold() + req.qty());
        List<HoldView> result = new ArrayList<>();
        if (units.isEmpty()) {
            result.add(HoldView.of(holds.save(hold(req, null, req.qty(), expiresAt)), w.getName(), null));
        }
        for (StockItem u : units) {
            u.setStatus(StockItem.Status.HOLD);
            u.setCurrentRefType(StockHold.RefType.MANUAL.name());
            u.setCurrentRefId(null);
            result.add(HoldView.of(holds.save(hold(req, u.getId(), 1, expiresAt)), w.getName(), u.getSerial()));
        }
        return result;
    }

    /** Gia hạn lượt giữ thủ công còn hiệu lực tới thời điểm mới (sau hạn hiện tại). */
    public HoldView extend(Long id, HoldExtendRequest req) {
        StockHold h = holds.lockById(id).orElseThrow(HoldService::notFound);
        checkAccess(h.getWarehouseId());
        requireManualActive(h);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!req.expiresAt().isAfter(now) || (h.getExpiresAt() != null && !req.expiresAt().isAfter(h.getExpiresAt()))) {
            throw invalid("expiresAt", "Hạn giữ mới phải sau hạn hiện tại và sau thời điểm hiện tại");
        }
        h.setExpiresAt(req.expiresAt());
        return view(h);
    }

    /** Nhả lượt giữ thủ công: trả lại tồn khả dụng, serial về AVAILABLE. Giữ cho chứng từ thì nhả qua chứng từ. */
    public HoldView release(Long id) {
        StockHold peek = holds.findById(id).orElseThrow(HoldService::notFound);
        checkAccess(peek.getWarehouseId());
        requireManualActive(peek);
        return view(finish(id, StockHold.Status.RELEASED));
    }

    /**
     * HoldExpiryJob: nhả lượt giữ thủ công đã quá hạn (status EXPIRED). Chạy ngoài HTTP request; mỗi lượt 1
     * transaction. Lượt giữ đã được nhả / gia hạn trong lúc chờ khóa thì bỏ qua.
     *
     * @return true nếu đã nhả
     */
    public boolean expire(Long id) {
        StockHold peek = holds.findById(id).orElse(null);
        if (peek == null || peek.getStatus() != StockHold.Status.ACTIVE) {
            return false;
        }
        balances.lock(peek.getProductId(), peek.getWarehouseId());
        StockHold h = holds.lockById(id).orElseThrow();
        if (h.getStatus() != StockHold.Status.ACTIVE || h.getExpiresAt() == null
                || h.getExpiresAt().isAfter(LocalDateTime.now(clock))) {
            return false;
        }
        finish(id, StockHold.Status.EXPIRED);
        return true;
    }

    /** Khóa dòng tồn → serial → lượt giữ rồi trả lại tồn khả dụng; kiểm lại còn ACTIVE sau khi khóa. */
    private StockHold finish(Long id, StockHold.Status status) {
        StockHold peek = holds.findById(id).orElseThrow(HoldService::notFound);
        StockBalance b = balances.lock(peek.getProductId(), peek.getWarehouseId()).orElseThrow();
        if (peek.getStockItemId() != null) {
            items.lockByIds(List.of(peek.getStockItemId())).forEach(u -> {
                u.setStatus(StockItem.Status.AVAILABLE);
                u.setCurrentRefType(null);
                u.setCurrentRefId(null);
            });
        }
        StockHold h = holds.lockById(id).orElseThrow(HoldService::notFound);
        if (h.getStatus() != StockHold.Status.ACTIVE) {
            throw new BusinessException(ErrorCode.CONFLICT, "Lượt giữ không còn hiệu lực");
        }
        b.setQtyHold(b.getQtyHold() - h.getQty());
        h.setStatus(status);
        return h;
    }

    // ------------------------------------------------------------------ hàng hỏng

    /**
     * Đánh dấu hàng hỏng: chuyển {@code qty} từ khả dụng sang qty_damaged (tồn thực không đổi). Nhật ký ghi loại
     * DAMAGED với qty_change = số lượng chuyển, qty_before / qty_after = số lượng hỏng trước / sau.
     */
    public DamagedResult markDamaged(DamagedRequest req) {
        checkAccess(req.warehouseId());
        requireWritable(req.warehouseId());
        List<String> serials = checkSerials(req.productId(), req.qty(), req.serials());
        StockBalance b = balances.lock(req.productId(), req.warehouseId()).orElse(null);
        int available = b == null ? 0 : b.available();
        if (b == null || available < req.qty()) {
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH, ErrorCode.STOCK_NOT_ENOUGH.defaultMessage(),
                    List.of(Map.of("productId", req.productId(), "requested", req.qty(), "available", available)));
        }
        List<StockItem> units = lockAvailableSerials(req.productId(), req.warehouseId(), serials);
        units.forEach(u -> u.setStatus(StockItem.Status.DAMAGED));
        int before = b.getQtyDamaged();
        b.setQtyDamaged(before + req.qty());

        StockMovement m = new StockMovement();
        m.setProductId(req.productId());
        m.setWarehouseId(req.warehouseId());
        m.setStockItemId(units.size() == 1 ? units.get(0).getId() : null);
        m.setMovementType(StockMovement.Type.DAMAGED);
        m.setRefType(StockMovement.RefType.MANUAL);
        m.setRefId(0); // ref_id NOT NULL; thao tác thủ công không có chứng từ
        m.setQtyChange(req.qty());
        m.setQtyBefore(before);
        m.setQtyAfter(before + req.qty());
        m.setUnitCost(b.getAvgCost());
        m.setUserId(currentUserId());
        movements.save(m);
        return new DamagedResult(b.getProductId(), b.getWarehouseId(), b.getQtyOnHand(), b.getQtyHold(),
                b.getQtyDamaged(), b.available());
    }

    /** Tồn sau khi đánh dấu hỏng. */
    public record DamagedResult(Integer productId, Integer warehouseId, int onHand, int hold, int damaged,
            int available) {
    }

    // ------------------------------------------------------------------ tiện ích

    /**
     * Sản phẩm quản lý serial (đã từng nhập serial) thì phải chỉ đúng serial, số serial = qty; không serial thì không
     * nhận serial. Serial không được lặp.
     */
    private List<String> checkSerials(Integer productId, int qty, List<String> serials) {
        List<String> list = serials == null ? List.of() : serials.stream().map(String::trim).toList();
        boolean serialProduct = items.existsByProductId(productId);
        if (serialProduct && list.size() != qty) {
            throw invalid("serials", "Sản phẩm " + productId + " quản lý serial: cần đúng " + qty + " serial");
        }
        if (!serialProduct && !list.isEmpty()) {
            throw invalid("serials", "Sản phẩm " + productId + " không quản lý serial");
        }
        if (new HashSet<>(list).size() != list.size()) {
            throw invalid("serials", "Serial bị lặp");
        }
        return list;
    }

    /** Khóa các serial; phải thuộc đúng sản phẩm, đang ở đúng kho và AVAILABLE. */
    private List<StockItem> lockAvailableSerials(Integer productId, Integer warehouseId, List<String> serials) {
        if (serials.isEmpty()) {
            return List.of();
        }
        Map<String, StockItem> found = items.lockBySerials(serials).stream()
                .collect(Collectors.toMap(StockItem::getSerial, Function.identity()));
        List<String> invalid = serials.stream().filter(s -> {
            StockItem i = found.get(s);
            return i == null || !i.getProductId().equals(productId) || !warehouseId.equals(i.getWarehouseId())
                    || i.getStatus() != StockItem.Status.AVAILABLE;
        }).toList();
        if (!invalid.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Serial không có sẵn tại kho",
                    Map.of("serials", invalid));
        }
        return serials.stream().map(found::get).toList();
    }

    private StockHold hold(HoldRequest req, Long stockItemId, int qty, LocalDateTime expiresAt) {
        StockHold h = new StockHold();
        h.setProductId(req.productId());
        h.setWarehouseId(req.warehouseId());
        h.setStockItemId(stockItemId);
        h.setQty(qty);
        h.setCustomerId(req.customerId());
        h.setCustomerName(StringUtils.hasText(req.customerName()) ? req.customerName().trim() : null);
        h.setHeldByUserId(currentUserId());
        h.setRefType(StockHold.RefType.MANUAL);
        h.setExpiresAt(expiresAt);
        return h;
    }

    private static void requireManualActive(StockHold h) {
        if (h.getRefType() != StockHold.RefType.MANUAL) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "Hàng đang giữ cho chứng từ " + h.getRefType() + " #" + h.getRefId() + ", hãy thao tác trên chứng từ");
        }
        if (h.getStatus() != StockHold.Status.ACTIVE) {
            throw new BusinessException(ErrorCode.CONFLICT, "Lượt giữ không còn hiệu lực");
        }
    }

    private Warehouse requireWritable(Integer warehouseId) {
        Warehouse w = warehouses.findById(warehouseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy kho"));
        if (w.getStatus() != Warehouse.Status.ACTIVE) {
            throw new BusinessException(ErrorCode.CONFLICT, "Kho " + w.getCode() + " không ở trạng thái hoạt động");
        }
        if (w.isStocktakeLocked()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Kho " + w.getCode() + " đang khóa giao dịch để kiểm kê");
        }
        return w;
    }

    private HoldView view(StockHold h) {
        String name = warehouses.findById(h.getWarehouseId()).map(Warehouse::getName).orElse(null);
        return HoldView.of(h, name, serialsOf(List.of(h)).get(h.getStockItemId()));
    }

    /** Serial theo stock_item_id; HashMap để get(null) (lượt giữ không serial) trả null thay vì lỗi. */
    private Map<Long, String> serialsOf(List<StockHold> list) {
        List<Long> ids = list.stream().map(StockHold::getStockItemId).filter(java.util.Objects::nonNull).toList();
        Map<Long, String> result = new java.util.HashMap<>();
        if (!ids.isEmpty()) {
            items.findAllById(ids).forEach(i -> result.put(i.getId(), i.getSerial()));
        }
        return result;
    }

    private Map<Integer, String> warehouseNames() {
        return warehouses.findAll().stream().collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));
    }

    private static void checkAccess(Integer warehouseId) {
        if (!UserContextHolder.get().canAccessWarehouse(warehouseId.longValue())) {
            throw new BusinessException(ErrorCode.WAREHOUSE_FORBIDDEN);
        }
    }

    private static Integer currentUserId() {
        Long id = UserContextHolder.get().userId();
        return id == null ? 0 : id.intValue();
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy lượt giữ hàng");
    }

    private static BusinessException invalid(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(),
                Map.of(field, message));
    }
}
