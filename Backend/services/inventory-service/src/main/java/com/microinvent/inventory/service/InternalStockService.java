package com.microinvent.inventory.service;

import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
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
import com.microinvent.inventory.web.dto.StockCheckRequest;
import com.microinvent.inventory.web.dto.StockCheckResponse;
import com.microinvent.inventory.web.dto.StockCommitRequest;
import com.microinvent.inventory.web.dto.StockReceiveRequest;
import com.microinvent.inventory.web.dto.StockReleaseRequest;
import com.microinvent.inventory.web.dto.StockReserveRequest;
import com.microinvent.inventory.web.dto.StockResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lõi tồn kho cho API nội bộ (SRS 3.10.7). Mọi thay đổi tồn chạy trong 1 transaction, đọc dòng tồn bằng
 * SELECT … FOR UPDATE để 2 request đồng thời không làm sai số tồn (kịch bản 8.5#5).
 */
@Service
@Transactional
public class InternalStockService {

    private static final Set<StockMovement.RefType> RECEIVE_REF_TYPES = Set.of(StockMovement.RefType.RECEIPT,
            StockMovement.RefType.RENTAL_RETURN, StockMovement.RefType.SALES_RETURN);
    /** Loại biến động hợp lệ khi commit, theo loại chứng từ đang giữ hàng. */
    private static final Map<StockHold.RefType, Set<StockMovement.Type>> COMMIT_TYPES = Map.of(
            StockHold.RefType.EXPORT_SLIP, Set.of(StockMovement.Type.EXPORT),
            StockHold.RefType.RENTAL, Set.of(StockMovement.Type.RENT, StockMovement.Type.LOAN));

    private final WarehouseRepository warehouses;
    private final StockBalanceRepository balances;
    private final StockItemRepository items;
    private final StockMovementRepository movements;
    private final StockHoldRepository holds;

    public InternalStockService(WarehouseRepository warehouses, StockBalanceRepository balances,
            StockItemRepository items, StockMovementRepository movements, StockHoldRepository holds) {
        this.warehouses = warehouses;
        this.balances = balances;
        this.items = items;
        this.movements = movements;
        this.holds = holds;
    }

    /** Không khóa – chỉ để báo trước cho người lập phiếu; reserve mới là bước giữ hàng thật. */
    @Transactional(readOnly = true)
    public StockCheckResponse check(StockCheckRequest req) {
        Map<Integer, String> names = warehouses.findAll().stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));
        List<StockCheckResponse.Line> lines = new ArrayList<>();
        for (StockCheckRequest.Item item : req.items()) {
            int available = balances.findByProductIdAndWarehouseId(item.productId(), req.warehouseId())
                    .map(StockBalance::available).orElse(0);
            int shortage = Math.max(0, item.qty() - available);
            List<StockCheckResponse.OtherWarehouse> others = shortage == 0 ? List.of()
                    : balances.findAvailableElsewhere(item.productId(), req.warehouseId()).stream()
                            .map(b -> new StockCheckResponse.OtherWarehouse(b.getWarehouseId(),
                                    names.get(b.getWarehouseId()), b.available()))
                            .toList();
            lines.add(new StockCheckResponse.Line(item.productId(), item.qty(), available, shortage, others));
        }
        return new StockCheckResponse(lines.stream().allMatch(l -> l.shortage() == 0), lines);
    }

    /** Cộng tồn khi nhập / nhận trả; tạo stock_items cho từng serial (serial trùng toàn hệ thống → 409). */
    public StockResult receive(StockReceiveRequest req) {
        if (!RECEIVE_REF_TYPES.contains(req.refType())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    Map.of("refType", "Chỉ nhận RECEIPT, RENTAL_RETURN, SALES_RETURN"));
        }
        requireWritable(req.warehouseId());
        checkSerials(req.lines());

        StockMovement.Type type = req.refType() == StockMovement.RefType.RECEIPT
                ? StockMovement.Type.IMPORT : StockMovement.Type.RETURN;
        LocalDateTime now = LocalDateTime.now();
        List<StockResult.Movement> result = new ArrayList<>();
        // Khóa theo thứ tự productId tăng dần: 2 phiếu cùng nhiều sản phẩm không khóa chéo nhau (deadlock).
        List<StockReceiveRequest.Line> lines = req.lines().stream()
                .sorted(Comparator.comparing(StockReceiveRequest.Line::productId)).toList();
        for (StockReceiveRequest.Line line : lines) {
            StockBalance b = lockOrCreate(line.productId(), req.warehouseId());
            boolean damaged = line.condition() == StockReceiveRequest.Condition.DAMAGED;
            BigDecimal unitCost = line.unitCost() != null ? line.unitCost() : b.getAvgCost();
            int before = b.getQtyOnHand();

            b.setAvgCost(weightedAverage(b.getAvgCost(), before, unitCost, line.qty()));
            b.setQtyOnHand(before + line.qty());
            if (damaged) {
                b.setQtyDamaged(b.getQtyDamaged() + line.qty());
            }
            if (line.serials() != null) {
                for (String serial : line.serials()) {
                    StockItem item = new StockItem();
                    item.setProductId(line.productId());
                    item.setWarehouseId(req.warehouseId());
                    item.setLastWarehouseId(req.warehouseId());
                    item.setSerial(serial.trim());
                    item.setStatus(damaged ? StockItem.Status.DAMAGED : StockItem.Status.AVAILABLE);
                    item.setCostPrice(unitCost);
                    item.setSupplierId(line.supplierId());
                    item.setReceiptId(req.refType() == StockMovement.RefType.RECEIPT ? req.refId() : null);
                    item.setReceivedAt(now);
                    items.save(item);
                }
            }
            result.add(StockResult.Movement.of(movements.save(movement(line.productId(), req.warehouseId(), type,
                    req.refType(), req.refId(), line.qty(), before, unitCost, req.userId()))));
        }
        return new StockResult(result, List.of(), List.of());
    }

    /**
     * Giữ hàng cho chứng từ: kiểm đủ tất cả các dòng rồi mới giữ (thiếu 1 dòng → không giữ dòng nào).
     * Thứ tự khóa dùng chung cho reserve / commit / release: dòng tồn (theo productId) → serial → hold.
     */
    public StockResult reserve(StockReserveRequest req) {
        if (!COMMIT_TYPES.containsKey(req.refType())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    Map.of("refType", "Chỉ nhận EXPORT_SLIP, RENTAL"));
        }
        requireWritable(req.warehouseId());
        if (!holds.findByRefTypeAndRefIdAndStatus(req.refType(), req.refId(), StockHold.Status.ACTIVE).isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Chứng từ đã giữ hàng, hãy release trước khi giữ lại");
        }
        Map<Integer, Integer> qtyByProduct = new TreeMap<>();
        Map<Integer, List<String>> serialsByProduct = new TreeMap<>();
        for (StockReserveRequest.Line line : req.lines()) {
            qtyByProduct.merge(line.productId(), line.qty(), Integer::sum);
            if (line.serials() != null) {
                if (line.serials().size() != line.qty()) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(),
                            Map.of("serials", "Sản phẩm " + line.productId() + ": số serial phải bằng số lượng " + line.qty()));
                }
                serialsByProduct.computeIfAbsent(line.productId(), k -> new ArrayList<>())
                        .addAll(line.serials().stream().map(String::trim).toList());
            }
        }

        Map<Integer, StockBalance> locked = new LinkedHashMap<>();
        List<Map<String, Object>> shortages = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : qtyByProduct.entrySet()) {
            StockBalance b = balances.lock(e.getKey(), req.warehouseId()).orElse(null);
            int available = b == null ? 0 : b.available();
            if (e.getValue() > available) {
                shortages.add(Map.of("productId", e.getKey(), "requested", e.getValue(), "available", available));
            }
            locked.put(e.getKey(), b);
        }
        if (!shortages.isEmpty()) {
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH, ErrorCode.STOCK_NOT_ENOUGH.defaultMessage(),
                    shortages);
        }
        Map<String, StockItem> held = lockAvailableSerials(serialsByProduct, req.warehouseId());

        List<Long> holdIds = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : qtyByProduct.entrySet()) {
            StockBalance b = locked.get(e.getKey());
            b.setQtyHold(b.getQtyHold() + e.getValue());
            List<String> serials = serialsByProduct.getOrDefault(e.getKey(), List.of());
            for (String serial : serials) {
                StockItem item = held.get(serial);
                item.setStatus(StockItem.Status.HOLD);
                item.setCurrentRefType(req.refType().name());
                item.setCurrentRefId(req.refId());
                holdIds.add(holds.save(hold(req, e.getKey(), item.getId(), 1)).getId());
            }
            if (e.getValue() > serials.size()) {
                holdIds.add(holds.save(hold(req, e.getKey(), null, e.getValue() - serials.size())).getId());
            }
        }
        return new StockResult(List.of(), holdIds, List.of());
    }

    /** Trừ tồn thực theo toàn bộ hold ACTIVE của chứng từ (xuất bán / cho thuê / cho mượn). */
    public StockResult commit(StockCommitRequest req) {
        if (!COMMIT_TYPES.getOrDefault(req.refType(), Set.of()).contains(req.movementType())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    Map.of("movementType", "EXPORT_SLIP → EXPORT; RENTAL → RENT hoặc LOAN"));
        }
        List<StockHold> active = lockHolds(req.refType(), req.refId());
        if (active.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Chứng từ không có hàng đang giữ để xuất");
        }
        requireWritable(active.get(0).getWarehouseId());
        StockItem.Status itemStatus = switch (req.movementType()) {
            case RENT -> StockItem.Status.RENTED;
            case LOAN -> StockItem.Status.LOANED;
            default -> StockItem.Status.SOLD;
        };
        Map<Long, StockItem> itemById = lockItemsOf(active);

        List<StockResult.Movement> result = new ArrayList<>();
        List<StockResult.UnitCost> unitCosts = new ArrayList<>();
        for (Map.Entry<Integer, List<StockHold>> e : byProduct(active).entrySet()) {
            Integer warehouseId = e.getValue().get(0).getWarehouseId();
            StockBalance b = balances.lock(e.getKey(), warehouseId).orElseThrow();
            int qty = e.getValue().stream().mapToInt(StockHold::getQty).sum();
            List<StockItem> serialItems = e.getValue().stream().map(StockHold::getStockItemId)
                    .filter(Objects::nonNull).map(itemById::get).toList();
            // Toàn bộ là hàng có serial: giá vốn theo đúng từng đơn vị; còn lại: giá vốn bình quân.
            BigDecimal unitCost = serialItems.size() != qty ? b.getAvgCost()
                    : serialItems.stream().map(StockItem::getCostPrice).reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(qty), 2, RoundingMode.HALF_UP);
            int before = b.getQtyOnHand();
            b.setQtyOnHand(before - qty);
            b.setQtyHold(b.getQtyHold() - qty);
            if (itemStatus != StockItem.Status.SOLD) {
                b.setQtyRentedLoaned(b.getQtyRentedLoaned() + qty);
            }
            for (StockItem item : serialItems) {
                item.setStatus(itemStatus);
                item.setWarehouseId(null);
                item.setLastWarehouseId(warehouseId);
            }
            e.getValue().forEach(h -> h.setStatus(StockHold.Status.CONSUMED));
            result.add(StockResult.Movement.of(movements.save(movement(e.getKey(), warehouseId, req.movementType(),
                    StockMovement.RefType.valueOf(req.refType().name()), req.refId(), -qty, before, unitCost,
                    req.userId()))));
            unitCosts.add(new StockResult.UnitCost(e.getKey(), unitCost));
        }
        return new StockResult(result, active.stream().map(StockHold::getId).toList(), unitCosts);
    }

    /** Nhả hàng đang giữ của chứng từ (hủy / từ chối). Gọi lại khi đã nhả hết thì không làm gì. */
    public StockResult release(StockReleaseRequest req) {
        List<StockHold> active = lockHolds(req.refType(), req.refId());
        Map<Long, StockItem> itemById = lockItemsOf(active);
        for (Map.Entry<Integer, List<StockHold>> e : byProduct(active).entrySet()) {
            StockBalance b = balances.lock(e.getKey(), e.getValue().get(0).getWarehouseId()).orElseThrow();
            b.setQtyHold(b.getQtyHold() - e.getValue().stream().mapToInt(StockHold::getQty).sum());
            for (StockHold h : e.getValue()) {
                h.setStatus(StockHold.Status.RELEASED);
                if (h.getStockItemId() != null) {
                    StockItem item = itemById.get(h.getStockItemId());
                    item.setStatus(StockItem.Status.AVAILABLE);
                    item.setCurrentRefType(null);
                    item.setCurrentRefId(null);
                }
            }
        }
        return new StockResult(List.of(), active.stream().map(StockHold::getId).toList(), List.of());
    }

    /**
     * Khóa dòng tồn các sản phẩm của chứng từ trước rồi mới khóa hold – cùng thứ tự khóa với reserve
     * (tồn → serial → hold). Hold của 1 chứng từ luôn cùng 1 kho.
     */
    private List<StockHold> lockHolds(StockHold.RefType refType, Integer refId) {
        List<StockHold> peek = holds.findByRefTypeAndRefIdAndStatus(refType, refId, StockHold.Status.ACTIVE);
        if (peek.isEmpty()) {
            return List.of();
        }
        Integer warehouseId = peek.get(0).getWarehouseId();
        peek.stream().map(StockHold::getProductId).distinct().sorted()
                .forEach(productId -> balances.lock(productId, warehouseId));
        return holds.lockActiveByRef(refType, refId);
    }

    private Map<Long, StockItem> lockItemsOf(List<StockHold> active) {
        List<Long> ids = active.stream().map(StockHold::getStockItemId).filter(Objects::nonNull).toList();
        return ids.isEmpty() ? Map.of() : items.lockByIds(ids).stream()
                .collect(Collectors.toMap(StockItem::getId, i -> i));
    }

    private static Map<Integer, List<StockHold>> byProduct(List<StockHold> active) {
        return active.stream().collect(Collectors.groupingBy(StockHold::getProductId, TreeMap::new,
                Collectors.toList()));
    }

    /** Khóa các serial cần giữ; serial phải thuộc đúng sản phẩm, đang ở đúng kho và AVAILABLE. */
    private Map<String, StockItem> lockAvailableSerials(Map<Integer, List<String>> serialsByProduct,
            Integer warehouseId) {
        List<String> all = serialsByProduct.values().stream().flatMap(List::stream).toList();
        if (all.isEmpty()) {
            return Map.of();
        }
        Map<String, StockItem> found = items.lockBySerials(all).stream()
                .collect(Collectors.toMap(StockItem::getSerial, i -> i));
        List<String> invalid = new ArrayList<>();
        serialsByProduct.forEach((productId, serials) -> serials.forEach(s -> {
            StockItem i = found.get(s);
            if (i == null || !i.getProductId().equals(productId) || !warehouseId.equals(i.getWarehouseId())
                    || i.getStatus() != StockItem.Status.AVAILABLE) {
                invalid.add(s);
            }
        }));
        if (!invalid.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Serial không có sẵn tại kho để giữ",
                    Map.of("serials", invalid));
        }
        return found;
    }

    private static StockHold hold(StockReserveRequest req, Integer productId, Long stockItemId, int qty) {
        StockHold h = new StockHold();
        h.setProductId(productId);
        h.setWarehouseId(req.warehouseId());
        h.setStockItemId(stockItemId);
        h.setQty(qty);
        h.setCustomerId(req.customerId());
        h.setHeldByUserId(req.userId());
        h.setRefType(req.refType());
        h.setRefId(req.refId());
        h.setExpiresAt(req.expiresAt());
        return h;
    }

    /** Kho phải đang hoạt động và không bị khóa kiểm kê mới được thay đổi tồn. */
    private void requireWritable(Integer warehouseId) {
        Warehouse w = warehouses.findById(warehouseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy kho"));
        if (w.getStatus() != Warehouse.Status.ACTIVE) {
            throw new BusinessException(ErrorCode.CONFLICT, "Kho " + w.getCode() + " không ở trạng thái hoạt động");
        }
        if (w.isStocktakeLocked()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Kho " + w.getCode() + " đang khóa giao dịch để kiểm kê");
        }
    }

    /** Số serial = qty, không trùng trong yêu cầu và chưa có trong hệ thống. */
    private void checkSerials(List<StockReceiveRequest.Line> lines) {
        Set<String> all = new HashSet<>();
        for (StockReceiveRequest.Line line : lines) {
            if (line.serials() == null) {
                continue;
            }
            if (line.serials().size() != line.qty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(),
                        Map.of("serials", "Sản phẩm " + line.productId() + ": số serial phải bằng số lượng " + line.qty()));
            }
            for (String s : line.serials()) {
                if (!all.add(s.trim())) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(),
                            Map.of("serials", "Serial bị lặp trong yêu cầu: " + s.trim()));
                }
            }
        }
        if (!all.isEmpty()) {
            List<String> existing = items.findExistingSerials(all);
            if (!existing.isEmpty()) {
                throw new BusinessException(ErrorCode.CONFLICT, "Serial đã tồn tại trong hệ thống",
                        Map.of("serials", existing));
            }
        }
    }

    private StockBalance lockOrCreate(Integer productId, Integer warehouseId) {
        balances.insertIfAbsent(productId, warehouseId);
        return balances.lock(productId, warehouseId).orElseThrow();
    }

    /** Giá vốn bình quân gia quyền sau khi nhập thêm {@code qty} với giá {@code unitCost}. */
    static BigDecimal weightedAverage(BigDecimal avg, int onHand, BigDecimal unitCost, int qty) {
        if (onHand + qty == 0) {
            return avg;
        }
        return avg.multiply(BigDecimal.valueOf(onHand)).add(unitCost.multiply(BigDecimal.valueOf(qty)))
                .divide(BigDecimal.valueOf(onHand + qty), 2, RoundingMode.HALF_UP);
    }

    private static StockMovement movement(Integer productId, Integer warehouseId, StockMovement.Type type,
            StockMovement.RefType refType, Integer refId, int change, int before, BigDecimal unitCost,
            Integer userId) {
        StockMovement m = new StockMovement();
        m.setProductId(productId);
        m.setWarehouseId(warehouseId);
        m.setMovementType(type);
        m.setRefType(refType);
        m.setRefId(refId);
        m.setQtyChange(change);
        m.setQtyBefore(before);
        m.setQtyAfter(before + change);
        m.setUnitCost(unitCost);
        m.setUserId(userId);
        return m;
    }
}
