package com.microinvent.inventory.service;

import com.microinvent.common.context.UserContext;
import com.microinvent.common.context.UserContextHolder;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.common.web.PageResponse;
import com.microinvent.inventory.domain.StockBalance;
import com.microinvent.inventory.domain.StockItem;
import com.microinvent.inventory.domain.StockMovement;
import com.microinvent.inventory.domain.Warehouse;
import com.microinvent.inventory.repository.StockBalanceRepository;
import com.microinvent.inventory.repository.StockItemRepository;
import com.microinvent.inventory.repository.StockMovementRepository;
import com.microinvent.inventory.repository.WarehouseRepository;
import com.microinvent.inventory.web.dto.LowStockView;
import com.microinvent.inventory.web.dto.ProductStockView;
import com.microinvent.inventory.web.dto.StockBalanceView;
import com.microinvent.inventory.web.dto.StockItemView;
import com.microinvent.inventory.web.dto.StockResult;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Tra cứu tồn kho (SRS 3.10.4). Vai trò không có all_warehouses chỉ thấy kho được gán. */
@Service
@Transactional(readOnly = true)
public class StockQueryService {

    static final Set<String> BALANCE_SORT = Set.of("productId", "warehouseId", "qtyOnHand", "updatedAt");
    static final Set<String> ITEM_SORT = Set.of("serial", "productId", "status", "receivedAt");
    static final Set<String> MOVEMENT_SORT = Set.of("id", "createdAt", "productId", "warehouseId");

    private final WarehouseRepository warehouses;
    private final StockBalanceRepository balances;
    private final StockItemRepository items;
    private final StockMovementRepository movements;

    public StockQueryService(WarehouseRepository warehouses, StockBalanceRepository balances,
            StockItemRepository items, StockMovementRepository movements) {
        this.warehouses = warehouses;
        this.balances = balances;
        this.items = items;
        this.movements = movements;
    }

    public PageResponse<StockBalanceView> list(Integer warehouseId, Integer productId, Boolean hasStock,
            Pageable pageable) {
        checkSort(pageable.getSort(), BALANCE_SORT);
        Scope s = scope(warehouseId);
        Map<Integer, String> names = warehouseNames();
        return PageResponse.of(balances.search(s.all(), s.ids(), productId, hasStock, pageable)
                .map(b -> StockBalanceView.of(b, names.get(b.getWarehouseId()))));
    }

    public ProductStockView byProduct(Integer productId) {
        Scope s = scope(null);
        Map<Integer, String> names = warehouseNames();
        List<StockBalance> list = balances.findByProductInScope(productId, s.all(), s.ids());
        ProductStockView.Totals totals = new ProductStockView.Totals(
                list.stream().mapToInt(StockBalance::getQtyOnHand).sum(),
                list.stream().mapToInt(StockBalance::getQtyHold).sum(),
                list.stream().mapToInt(StockBalance::getQtyDamaged).sum(),
                list.stream().mapToInt(StockBalance::available).sum(),
                list.stream().mapToInt(StockBalance::getQtyInTransitIn).sum(),
                list.stream().mapToInt(StockBalance::getQtyRentedLoaned).sum());
        return new ProductStockView(productId, totals,
                list.stream().map(b -> StockBalanceView.of(b, names.get(b.getWarehouseId()))).toList());
    }

    /**
     * Tra cứu serial: đang ở kho nào / đang chuyển / đang thuê / đã bán (mọi vai trò – dùng cả khi bảo hành
     * hàng xuất từ kho khác). Lịch sử = biến động theo phiếu nhập và chứng từ hiện tại của serial.
     */
    public StockItemView bySerial(String serial) {
        StockItem item = items.findBySerial(serial.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy serial"));
        List<StockResult.Movement> history = new ArrayList<>();
        if (item.getReceiptId() != null) {
            history.addAll(movementsOf(item.getProductId(), StockMovement.RefType.RECEIPT, item.getReceiptId()));
        }
        if (item.getCurrentRefType() != null) {
            history.addAll(movementsOf(item.getProductId(), StockMovement.RefType.valueOf(item.getCurrentRefType()),
                    item.getCurrentRefId()));
        }
        return StockItemView.of(item, history);
    }

    public PageResponse<StockItemView> listItems(Integer warehouseId, Integer productId, StockItem.Status status,
            String keyword, Pageable pageable) {
        checkSort(pageable.getSort(), ITEM_SORT);
        Scope s = scope(warehouseId);
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return PageResponse.of(items.search(s.all(), s.ids(), productId, status, kw, pageable)
                .map(i -> StockItemView.of(i, null)));
    }

    public List<LowStockView> lowStock(Integer warehouseId) {
        Scope s = scope(warehouseId);
        Map<Integer, String> names = warehouseNames();
        return balances.findBelowMin(s.all(), s.ids()).stream()
                .map(r -> new LowStockView(r.getProductId(), r.getWarehouseId(), names.get(r.getWarehouseId()),
                        r.getAvailable(), r.getMinStock(), r.getMinStock() - r.getAvailable()))
                .toList();
    }

    /** {@code from} / {@code to} tính theo ngày, gồm cả ngày {@code to}. */
    public PageResponse<StockResult.Movement> listMovements(Integer warehouseId, Integer productId,
            StockMovement.Type type, LocalDate from, LocalDate to, Pageable pageable) {
        checkSort(pageable.getSort(), MOVEMENT_SORT);
        Scope s = scope(warehouseId);
        return PageResponse.of(movements.search(s.all(), s.ids(), productId, type,
                from == null ? null : from.atStartOfDay(), to == null ? null : to.plusDays(1).atStartOfDay(), pageable)
                .map(StockResult.Movement::of));
    }

    /**
     * Phạm vi kho của truy vấn: có {@code warehouseId} → chỉ kho đó (phải có quyền); không có → mọi kho nếu
     * all_warehouses, ngược lại các kho được gán.
     */
    private static Scope scope(Integer warehouseId) {
        UserContext user = UserContextHolder.get();
        if (warehouseId != null) {
            if (!user.canAccessWarehouse(warehouseId.longValue())) {
                throw new BusinessException(ErrorCode.WAREHOUSE_FORBIDDEN);
            }
            return new Scope(false, Set.of(warehouseId));
        }
        Set<Integer> ids = user.warehouseIds().stream().map(Long::intValue).collect(Collectors.toSet());
        return new Scope(user.allWarehouses(), ids.isEmpty() ? Set.of(-1) : ids); // tránh "IN ()" rỗng
    }

    private record Scope(boolean all, Set<Integer> ids) {
    }

    private Map<Integer, String> warehouseNames() {
        return warehouses.findAll().stream().collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));
    }

    private List<StockResult.Movement> movementsOf(Integer productId, StockMovement.RefType refType, Integer refId) {
        return movements.findByProductIdAndRefTypeAndRefIdOrderById(productId, refType, refId).stream()
                .map(StockResult.Movement::of).toList();
    }

    /** Trường sort lạ làm Hibernate lỗi khi dựng query (500) – chặn trước, trả 400. */
    private static void checkSort(Sort sort, Set<String> allowed) {
        for (Sort.Order order : sort) {
            if (!allowed.contains(order.getProperty())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(),
                        Map.of("sort", "Chỉ sắp xếp được theo: " + String.join(", ", allowed.stream().sorted().toList())));
            }
        }
    }
}
