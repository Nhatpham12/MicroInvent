package com.microinvent.procurement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.microinvent.common.context.UserContext;
import com.microinvent.common.context.UserContextHolder;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.common.web.PageResponse;
import com.microinvent.procurement.client.CatalogClient;
import com.microinvent.procurement.client.InventoryClient;
import com.microinvent.procurement.domain.GoodsReceipt;
import com.microinvent.procurement.domain.GoodsReceiptItem;
import com.microinvent.procurement.domain.OutboxEvent;
import com.microinvent.procurement.domain.VatRate;
import com.microinvent.procurement.repository.GoodsReceiptRepository;
import com.microinvent.procurement.repository.OutboxEventRepository;
import com.microinvent.procurement.web.dto.ReceiptRequest;
import com.microinvent.procurement.web.dto.ReceiptResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Phiếu nhập kho (SRS 3.3.1). Nháp → xác nhận (cộng tồn ở inventory) → có thể sửa / hủy (đảo tồn).
 * Mọi thay đổi tồn đi qua API nội bộ của inventory-service kèm Idempotency-Key; event ghi outbox cùng transaction.
 */
@Service
@Transactional
public class ReceiptService {

    public static final String PREFIX = "PNK";
    static final Set<String> SORTABLE = Set.of("receiptDate", "code", "totalAmount", "createdAt", "id");

    private final GoodsReceiptRepository receipts;
    private final OutboxEventRepository outbox;
    private final InventoryClient inventory;
    private final CatalogClient catalog;

    public ReceiptService(GoodsReceiptRepository receipts, OutboxEventRepository outbox, InventoryClient inventory,
            CatalogClient catalog) {
        this.receipts = receipts;
        this.outbox = outbox;
        this.inventory = inventory;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public PageResponse<ReceiptResponse> list(Integer warehouseId, Integer supplierId, GoodsReceipt.Status status,
            LocalDate from, LocalDate to, String keyword, Pageable pageable) {
        for (Sort.Order o : pageable.getSort()) {
            if (!SORTABLE.contains(o.getProperty())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(),
                        Map.of("sort", "Chỉ sắp xếp được theo: " + String.join(", ", SORTABLE.stream().sorted().toList())));
            }
        }
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
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return PageResponse.of(receipts.search(all, ids, supplierId, status,
                from == null ? null : from.atStartOfDay(), to == null ? null : to.plusDays(1).atStartOfDay(), kw,
                pageable).map(r -> ReceiptResponse.of(r, false)));
    }

    @Transactional(readOnly = true)
    public ReceiptResponse get(Integer id) {
        GoodsReceipt r = receipts.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy phiếu nhập"));
        checkAccess(r.getWarehouseId());
        return ReceiptResponse.of(r, true);
    }

    public ReceiptResponse create(ReceiptRequest req) {
        checkAccess(req.warehouseId());
        GoodsReceipt r = new GoodsReceipt();
        r.setCode(nextCode(LocalDate.now().getYear()));
        r.setCreatedBy(currentUserId());
        apply(r, req);
        return ReceiptResponse.of(receipts.save(r), true);
    }

    /** Phiếu nháp: sửa thẳng. Phiếu đã xác nhận: đảo tồn theo dòng cũ rồi cộng theo dòng mới (lỗi → nhập lại dòng cũ). */
    public ReceiptResponse update(Integer id, ReceiptRequest req) {
        GoodsReceipt r = lock(id);
        checkAccess(req.warehouseId());
        if (r.getStatus() == GoodsReceipt.Status.CANCELLED) {
            throw new BusinessException(ErrorCode.CONFLICT, "Phiếu đã hủy, không sửa được");
        }
        if (r.getStatus() == GoodsReceipt.Status.DRAFT) {
            apply(r, req);
            return ReceiptResponse.of(r, true);
        }
        InventoryClient.StockRequest before = stockRequest(r);
        apply(r, req);
        receipts.flush(); // ghi DB trước: lỗi dữ liệu thì dừng ở đây, chưa đụng tới tồn kho
        InventoryClient.StockRequest after = stockRequest(r);
        String edit = "RECEIPT:" + id + ":edit:" + UUID.randomUUID();
        downstream(() -> inventory.reverse(edit + ":reverse", before));
        try {
            downstream(() -> inventory.receive(edit + ":receive", after));
        } catch (BusinessException ex) {
            downstream(() -> inventory.receive(edit + ":restore", before)); // trả tồn về như cũ rồi báo lỗi
            throw ex;
        }
        addEvent("receipt.updated", r);
        return ReceiptResponse.of(r, true);
    }

    /** Xác nhận → cộng tồn kho nhận (8.5#1). Bấm lại / 2 request cùng lúc: phiếu đã khóa, chỉ 1 lần cộng. */
    public ReceiptResponse confirm(Integer id) {
        GoodsReceipt r = lock(id);
        if (r.getStatus() != GoodsReceipt.Status.DRAFT) {
            throw new BusinessException(ErrorCode.CONFLICT, "Chỉ xác nhận được phiếu nháp");
        }
        downstream(() -> inventory.receive("RECEIPT:" + id + ":confirm", stockRequest(r)));
        r.setStatus(GoodsReceipt.Status.CONFIRMED);
        r.setConfirmedBy(currentUserId());
        r.setConfirmedAt(LocalDateTime.now());
        addEvent("receipt.confirmed", r);
        return ReceiptResponse.of(r, true);
    }

    /** Hủy phiếu; đã xác nhận thì đảo tồn (hàng đã bị giữ / xuất → 409 từ inventory, không hủy). */
    public ReceiptResponse cancel(Integer id) {
        GoodsReceipt r = lock(id);
        if (r.getStatus() == GoodsReceipt.Status.CANCELLED) {
            throw new BusinessException(ErrorCode.CONFLICT, "Phiếu đã hủy");
        }
        if (r.getStatus() == GoodsReceipt.Status.CONFIRMED) {
            downstream(() -> inventory.reverse("RECEIPT:" + id + ":cancel", stockRequest(r)));
            addEvent("receipt.cancelled", r);
        }
        r.setStatus(GoodsReceipt.Status.CANCELLED);
        return ReceiptResponse.of(r, true);
    }

    private GoodsReceipt lock(Integer id) {
        GoodsReceipt r = receipts.lock(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy phiếu nhập"));
        checkAccess(r.getWarehouseId());
        return r;
    }

    /** Ghi thông tin phiếu + thay toàn bộ dòng; snapshot sản phẩm lấy từ catalog, kiểm quy tắc serial. */
    private void apply(GoodsReceipt r, ReceiptRequest req) {
        GoodsReceipt.SourceType source = req.sourceType() != null ? req.sourceType() : GoodsReceipt.SourceType.SUPPLIER;
        if (source == GoodsReceipt.SourceType.SUPPLIER && req.supplierId() == null) {
            throw invalid("supplierId", "Phiếu nhập từ nhà cung cấp phải chọn nhà cung cấp");
        }
        if (source == GoodsReceipt.SourceType.RETAIL_CUSTOMER && req.customerId() == null) {
            throw invalid("customerId", "Phiếu mua lại của khách lẻ phải chọn khách hàng");
        }
        Map<Integer, Map<String, Object>> products = loadProducts(req.lines());
        checkSerials(req.lines(), products);

        r.setWarehouseId(req.warehouseId());
        r.setSourceType(source);
        r.setSupplierId(source == GoodsReceipt.SourceType.SUPPLIER ? req.supplierId() : null);
        r.setCustomerId(source == GoodsReceipt.SourceType.RETAIL_CUSTOMER ? req.customerId() : null);
        r.setReceiptDate(req.receiptDate() != null ? req.receiptDate() : LocalDateTime.now());
        r.setContractNo(trimToNull(req.contractNo()));
        r.setTrackingNo(trimToNull(req.trackingNo()));
        r.setBankPaymentRef(trimToNull(req.bankPaymentRef()));
        r.setVatRate(req.vatRate() != null ? req.vatRate() : VatRate.NONE);
        r.setNote(trimToNull(req.note()));

        r.getItems().clear();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ReceiptRequest.Line l : req.lines()) {
            GoodsReceiptItem i = new GoodsReceiptItem();
            i.setReceipt(r);
            i.setProductId(l.productId());
            i.setProductSnapshot(products.get(l.productId()));
            i.setQty(l.qty());
            i.setUnitPrice(l.unitPrice());
            i.setLineTotal(l.unitPrice().multiply(BigDecimal.valueOf(l.qty())));
            i.setSerials(l.serials() == null || l.serials().isEmpty() ? null
                    : l.serials().stream().map(String::trim).toList());
            i.setConditionNote(trimToNull(l.conditionNote()));
            r.getItems().add(i);
            subtotal = subtotal.add(i.getLineTotal());
        }
        BigDecimal vat = subtotal.multiply(r.getVatRate().percent()).divide(BigDecimal.valueOf(100), 2,
                RoundingMode.HALF_UP);
        r.setSubtotal(subtotal);
        r.setVatAmount(vat);
        r.setTotalAmount(subtotal.add(vat));
    }

    private Map<Integer, Map<String, Object>> loadProducts(List<ReceiptRequest.Line> lines) {
        Map<Integer, Map<String, Object>> result = new LinkedHashMap<>();
        for (Integer productId : lines.stream().map(ReceiptRequest.Line::productId).distinct().toList()) {
            JsonNode p;
            try {
                p = downstream(() -> catalog.getProduct(productId)).path("data");
            } catch (BusinessException ex) {
                if (ex.getCode() == ErrorCode.NOT_FOUND) {
                    throw invalid("lines", "Sản phẩm " + productId + " không tồn tại");
                }
                throw ex;
            }
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("code", p.path("code").asText());
            snapshot.put("name", p.path("name").asText());
            snapshot.put("unit", p.path("unit").asText());
            snapshot.put("hasSerial", p.path("hasSerial").asBoolean());
            result.put(productId, snapshot);
        }
        return result;
    }

    /** Sản phẩm có serial: số serial = số lượng; không serial: không nhận serial; serial không lặp trong phiếu. */
    private static void checkSerials(List<ReceiptRequest.Line> lines, Map<Integer, Map<String, Object>> products) {
        Set<String> seen = new HashSet<>();
        for (ReceiptRequest.Line l : lines) {
            boolean hasSerial = Boolean.TRUE.equals(products.get(l.productId()).get("hasSerial"));
            int count = l.serials() == null ? 0 : l.serials().size();
            if (hasSerial && count != l.qty()) {
                throw invalid("serials", "Sản phẩm " + products.get(l.productId()).get("code")
                        + " có serial: cần đúng " + l.qty() + " serial");
            }
            if (!hasSerial && count > 0) {
                throw invalid("serials", "Sản phẩm " + products.get(l.productId()).get("code") + " không quản lý serial");
            }
            if (l.serials() != null) {
                for (String s : l.serials()) {
                    if (!seen.add(s.trim())) {
                        throw invalid("serials", "Serial bị lặp trong phiếu: " + s.trim());
                    }
                }
            }
        }
    }

    private InventoryClient.StockRequest stockRequest(GoodsReceipt r) {
        return new InventoryClient.StockRequest(r.getWarehouseId(), "RECEIPT", r.getId(), currentUserId(),
                r.getItems().stream().map(i -> new InventoryClient.Line(i.getProductId(), i.getQty(), i.getSerials(),
                        i.getUnitPrice(), r.getSupplierId())).toList());
    }

    private void addEvent(String type, GoodsReceipt r) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("receiptId", r.getId());
        payload.put("code", r.getCode());
        payload.put("warehouseId", r.getWarehouseId());
        payload.put("supplierId", r.getSupplierId());
        payload.put("receiptDate", r.getReceiptDate().toString());
        payload.put("totalAmount", r.getTotalAmount());
        payload.put("lines", r.getItems().stream().map(i -> Map.of("productId", i.getProductId(), "qty", i.getQty(),
                "unitPrice", i.getUnitPrice())).toList());
        OutboxEvent e = new OutboxEvent();
        e.setEventId(UUID.randomUUID().toString());
        e.setEventType(type);
        e.setAggregateId(r.getId());
        e.setPayload(payload);
        outbox.save(e);
    }

    /** PNK-yyyy-nnnn; INSERT … ON DUPLICATE KEY UPDATE khóa dòng sequence tới hết transaction. */
    private String nextCode(int year) {
        receipts.nextSequence(PREFIX, year);
        return "%s-%d-%04d".formatted(PREFIX, year, receipts.currentSequence(PREFIX, year));
    }

    /** Lỗi gọi sang service khác: lỗi nghiệp vụ giữ nguyên mã; mất kết nối / quá tải → 503. */
    private static <T> T downstream(Supplier<T> call) {
        try {
            return call.get();
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Không gọi được dịch vụ liên quan, thử lại sau");
        }
    }

    private static void checkAccess(Integer warehouseId) {
        if (!UserContextHolder.get().canAccessWarehouse(warehouseId.longValue())) {
            throw new BusinessException(ErrorCode.WAREHOUSE_FORBIDDEN);
        }
    }

    private static Integer currentUserId() {
        return UserContextHolder.get().userId().intValue();
    }

    private static BusinessException invalid(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(),
                Map.of(field, message));
    }

    private static String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
