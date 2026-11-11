package com.microinvent.inventory.service;

import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.inventory.domain.StockBalance;
import com.microinvent.inventory.domain.StockItem;
import com.microinvent.inventory.domain.StockMovement;
import com.microinvent.inventory.domain.Warehouse;
import com.microinvent.inventory.repository.StockBalanceRepository;
import com.microinvent.inventory.repository.StockItemRepository;
import com.microinvent.inventory.repository.StockMovementRepository;
import com.microinvent.inventory.repository.WarehouseRepository;
import com.microinvent.inventory.web.dto.StockCheckRequest;
import com.microinvent.inventory.web.dto.StockCheckResponse;
import com.microinvent.inventory.web.dto.StockReceiveRequest;
import com.microinvent.inventory.web.dto.StockResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private final WarehouseRepository warehouses;
    private final StockBalanceRepository balances;
    private final StockItemRepository items;
    private final StockMovementRepository movements;

    public InternalStockService(WarehouseRepository warehouses, StockBalanceRepository balances,
            StockItemRepository items, StockMovementRepository movements) {
        this.warehouses = warehouses;
        this.balances = balances;
        this.items = items;
        this.movements = movements;
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
