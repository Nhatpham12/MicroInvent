package com.microinvent.sales.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.microinvent.common.context.UserContext;
import com.microinvent.common.context.UserContextHolder;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.common.web.PageResponse;
import com.microinvent.sales.client.CatalogClient;
import com.microinvent.sales.client.InventoryClient;
import com.microinvent.sales.domain.ExportSlip;
import com.microinvent.sales.domain.ExportSlipItem;
import com.microinvent.sales.domain.VatRate;
import com.microinvent.sales.repository.ExportSlipRepository;
import com.microinvent.sales.web.dto.AvailabilityRequest;
import com.microinvent.sales.web.dto.ExportSlipRequest;
import com.microinvent.sales.web.dto.ExportSlipResponse;
import com.microinvent.sales.web.dto.SplitRequest;
import com.microinvent.tenant.outbox.OutboxWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Phiếu xuất kho (SRS 3.4.1). Mọi thay đổi tồn đi qua API nội bộ của inventory-service (refType EXPORT_SLIP,
 * refId = id phiếu) kèm Idempotency-Key; event ghi outbox cùng transaction (OutboxWriter của tenant-starter).
 * <ul>
 * <li>Tạo / sửa: giữ đủ hàng tại kho xuất. Kho xuất thiếu hàng → phiếu vẫn lưu PENDING nhưng chưa giữ
 * ({@code stockReserved = false}), trả kèm dòng thiếu + kho khác còn hàng để tách phiếu theo kho.</li>
 * <li>Xác nhận: PENDING → CONFIRMED, chỉ khi đã giữ đủ hàng.</li>
 * <li>Xuất: CONFIRMED → EXPORTED, trừ tồn thực theo hàng đang giữ, ghi giá vốn + hạn bảo hành, phát export.exported.</li>
 * <li>Hủy: trước khi xuất, nhả hàng đang giữ.</li>
 * </ul>
 */
@Service
@Transactional
public class ExportSlipService {

    public static final String PREFIX = "PXK";
    static final String REF_TYPE = "EXPORT_SLIP";
    static final String EVENT_EXPORTED = "export.exported";

    private final ExportSlipRepository slips;
    private final InventoryClient inventory;
    private final CatalogClient catalog;
    private final OutboxWriter outbox;

    public ExportSlipService(ExportSlipRepository slips, InventoryClient inventory, CatalogClient catalog,
            OutboxWriter outbox) {
        this.slips = slips;
        this.inventory = inventory;
        this.catalog = catalog;
        this.outbox = outbox;
    }

    // ------------------------------------------------------------------ tra cứu

    @Transactional(readOnly = true)
    public PageResponse<ExportSlipResponse> list(Integer warehouseId, Integer customerId, ExportSlip.Status status,
            ExportSlip.ExportType exportType, LocalDate from, LocalDate to, String keyword, Pageable pageable) {
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
        return PageResponse.of(slips.search(all, ids, customerId, status, exportType,
                from == null ? null : from.atStartOfDay(), to == null ? null : to.plusDays(1).atStartOfDay(),
                likePattern(keyword), pageable).map(s -> ExportSlipResponse.of(s, false)));
    }

    @Transactional(readOnly = true)
    public ExportSlipResponse get(Integer id) {
        ExportSlip s = slips.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy phiếu xuất"));
        checkAccess(s.getWarehouseId());
        return ExportSlipResponse.of(s, true);
    }

    /** Tồn khả dụng tại kho xuất + kho khác còn hàng (chỉ đọc, không giữ hàng). */
    @Transactional(readOnly = true)
    public JsonNode checkAvailability(AvailabilityRequest req) {
        checkAccess(req.warehouseId());
        Map<Integer, Integer> qty = new LinkedHashMap<>();
        req.items().forEach(i -> qty.merge(i.productId(), i.qty(), Integer::sum));
        return check(req.warehouseId(), qty);
    }

    // ------------------------------------------------------------------ lập / sửa phiếu

    public ExportSlipResponse create(ExportSlipRequest req) {
        checkAccess(req.warehouseId());
        ExportSlip s = new ExportSlip();
        s.setCode(nextCode(LocalDate.now().getYear()));
        s.setCreatedBy(currentUserId());
        apply(s, req);
        slips.saveAndFlush(s); // cần id phiếu làm refId khi giữ hàng
        return ExportSlipResponse.of(s, true, reserve(s));
    }

    /**
     * Sửa khi chưa xuất: nhả hàng đang giữ theo dòng cũ rồi giữ lại theo dòng mới (phiếu đã xác nhận quay về
     * PENDING). Giữ lại lỗi không phải do thiếu hàng (vd serial đã bị giữ) → giữ lại dòng cũ rồi báo lỗi.
     */
    public ExportSlipResponse update(Integer id, ExportSlipRequest req) {
        ExportSlip s = lock(id);
        requireNotExported(s, "sửa");
        checkAccess(req.warehouseId());
        boolean wasReserved = s.isStockReserved();
        InventoryClient.ReserveRequest before = reserveRequest(s);

        apply(s, req);
        s.setStatus(ExportSlip.Status.PENDING);
        s.setConfirmedBy(null);
        slips.flush(); // lỗi dữ liệu dừng ở đây, chưa đụng tới tồn kho

        if (wasReserved) {
            release(s.getId(), "edit");
        }
        s.setStockReserved(false);
        try {
            return ExportSlipResponse.of(s, true, reserve(s));
        } catch (BusinessException ex) {
            if (wasReserved) {
                downstream(() -> inventory.reserve(key(id, "restore"), before)); // trả hàng giữ về như cũ
            }
            throw ex;
        }
    }

    /**
     * Tách phiếu theo kho (SRS 3.4.1): chuyển một phần số lượng / serial sang phiếu con (split_from_id) xuất ở kho
     * khác, rồi giữ hàng lại cho phiếu gốc và từng phiếu con. Phiếu nào kho vẫn thiếu thì lưu chưa giữ (như lúc tạo).
     * Lỗi khác khi giữ hàng → nhả hết hàng vừa giữ, trả lại hàng giữ cũ của phiếu gốc rồi báo lỗi.
     */
    public ExportSlipResponse split(Integer id, SplitRequest req) {
        ExportSlip s = lock(id);
        if (s.getStatus() != ExportSlip.Status.PENDING) {
            throw new BusinessException(ErrorCode.CONFLICT, "Chỉ tách được phiếu đang chờ xác nhận");
        }
        Set<Integer> warehouses = new HashSet<>();
        for (SplitRequest.Allocation a : req.allocations()) {
            if (a.warehouseId().equals(s.getWarehouseId())) {
                throw invalid("allocations", "Kho tách phải khác kho xuất của phiếu gốc");
            }
            if (!warehouses.add(a.warehouseId())) {
                throw invalid("allocations", "Kho " + a.warehouseId() + " bị lặp, gộp các dòng vào một phiếu");
            }
            checkAccess(a.warehouseId());
        }
        boolean wasReserved = s.isStockReserved();
        InventoryClient.ReserveRequest before = reserveRequest(s);

        List<ExportSlip> children = new ArrayList<>();
        for (SplitRequest.Allocation a : req.allocations()) {
            ExportSlip child = childOf(s, a.warehouseId());
            for (SplitRequest.Line l : a.lines()) {
                moveLine(s, child, l);
            }
            recalcTotals(child, child.getItems().stream().map(ExportSlipItem::getLineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            children.add(child);
        }
        if (s.getItems().isEmpty()) {
            throw invalid("allocations", "Không tách hết phiếu; muốn đổi kho xuất thì sửa phiếu");
        }
        recalcTotals(s, s.getItems().stream().map(ExportSlipItem::getLineTotal).reduce(BigDecimal.ZERO,
                BigDecimal::add));
        children.forEach(slips::saveAndFlush);
        slips.flush();

        List<Integer> reserved = new ArrayList<>();
        try {
            if (wasReserved) {
                release(s.getId(), "split");
            }
            s.setStockReserved(false);
            ExportSlipResponse.ReserveOutcome outcome = reserve(s);
            if (s.isStockReserved()) {
                reserved.add(s.getId());
            }
            List<ExportSlipResponse> childResponses = new ArrayList<>();
            for (ExportSlip c : children) {
                ExportSlipResponse.ReserveOutcome o = reserve(c);
                if (c.isStockReserved()) {
                    reserved.add(c.getId());
                }
                childResponses.add(ExportSlipResponse.of(c, true, o));
            }
            return ExportSlipResponse.of(s, true, outcome, childResponses);
        } catch (BusinessException ex) {
            reserved.forEach(slipId -> release(slipId, "rollback"));
            if (wasReserved) {
                downstream(() -> inventory.reserve(key(id, "restore"), before));
            }
            throw ex;
        }
    }

    /** Phiếu con: cùng loại phiếu, khách, VAT, nhân viên bán; kho xuất khác; cọc / hạn thanh toán ở phiếu gốc. */
    private ExportSlip childOf(ExportSlip s, Integer warehouseId) {
        ExportSlip c = new ExportSlip();
        c.setCode(nextCode(LocalDate.now().getYear()));
        c.setExportType(s.getExportType());
        c.setWarehouseId(warehouseId);
        c.setCustomerId(s.getCustomerId());
        c.setCustomerSnapshot(s.getCustomerSnapshot());
        c.setDeliveryMethod(s.getDeliveryMethod());
        c.setVatRate(s.getVatRate());
        c.setSalesUserId(s.getSalesUserId());
        c.setNote(s.getNote());
        c.setSplitFromId(s.getId());
        c.setCreatedBy(currentUserId());
        return c;
    }

    /**
     * Chuyển {@code l.qty()} của sản phẩm từ phiếu gốc sang phiếu con (giữ đơn giá của dòng gốc). Sản phẩm có serial:
     * chuyển đúng các serial đã chỉ ra (phải thuộc phiếu gốc); không serial: trừ dần theo thứ tự dòng.
     */
    private static void moveLine(ExportSlip from, ExportSlip to, SplitRequest.Line l) {
        List<ExportSlipItem> lines = from.getItems().stream().filter(i -> i.getProductId().equals(l.productId()))
                .toList();
        if (lines.isEmpty()) {
            throw invalid("allocations", "Sản phẩm " + l.productId() + " không có trên phiếu gốc");
        }
        int total = lines.stream().mapToInt(ExportSlipItem::getQty).sum();
        if (l.qty() > total) {
            throw invalid("allocations", "Sản phẩm " + l.productId() + " chỉ còn " + total + " trên phiếu gốc");
        }
        boolean hasSerial = Boolean.TRUE.equals(lines.get(0).getProductSnapshot().get("hasSerial"));
        List<String> serials = l.serials() == null ? List.of() : l.serials().stream().map(String::trim).toList();
        if (hasSerial && serials.size() != l.qty()) {
            throw invalid("allocations", "Sản phẩm " + l.productId() + " có serial: cần đúng " + l.qty() + " serial");
        }
        if (!hasSerial && !serials.isEmpty()) {
            throw invalid("allocations", "Sản phẩm " + l.productId() + " không quản lý serial");
        }
        if (new HashSet<>(serials).size() != serials.size()) {
            throw invalid("allocations", "Serial bị lặp (sản phẩm " + l.productId() + ")");
        }
        int remaining = l.qty();
        for (ExportSlipItem src : lines) {
            if (remaining == 0) {
                break;
            }
            List<String> moved = hasSerial
                    ? serials.stream().filter(sn -> src.getSerials() != null && src.getSerials().contains(sn)).toList()
                    : List.of();
            int take = hasSerial ? moved.size() : Math.min(remaining, src.getQty());
            if (take == 0) {
                continue;
            }
            ExportSlipItem dst = new ExportSlipItem();
            dst.setSlip(to);
            dst.setProductId(src.getProductId());
            dst.setProductSnapshot(src.getProductSnapshot());
            dst.setQty(take);
            dst.setUnitPrice(src.getUnitPrice());
            dst.setLineTotal(src.getUnitPrice().multiply(BigDecimal.valueOf(take)));
            dst.setSerials(hasSerial ? moved : null);
            dst.setWarrantyMonths(src.getWarrantyMonths());
            to.getItems().add(dst);

            src.setQty(src.getQty() - take);
            src.setLineTotal(src.getUnitPrice().multiply(BigDecimal.valueOf(src.getQty())));
            if (hasSerial) {
                List<String> left = new ArrayList<>(src.getSerials());
                left.removeAll(moved);
                src.setSerials(left.isEmpty() ? null : left);
            }
            if (src.getQty() == 0) {
                from.getItems().remove(src);
            }
            remaining -= take;
        }
        if (remaining > 0) {
            throw invalid("allocations", "Serial chuyển đi phải nằm trên phiếu gốc (sản phẩm " + l.productId() + ")");
        }
    }

    // ------------------------------------------------------------------ đổi trạng thái

    /** PENDING → CONFIRMED; chỉ khi đã giữ đủ hàng tại kho xuất. */
    public ExportSlipResponse confirm(Integer id) {
        ExportSlip s = lock(id);
        if (s.getStatus() != ExportSlip.Status.PENDING) {
            throw new BusinessException(ErrorCode.CONFLICT, "Chỉ xác nhận được phiếu đang chờ xác nhận");
        }
        if (!s.isStockReserved()) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "Phiếu chưa giữ đủ hàng tại kho xuất, hãy tách phiếu theo kho hoặc sửa số lượng");
        }
        s.setStatus(ExportSlip.Status.CONFIRMED);
        s.setConfirmedBy(currentUserId());
        return ExportSlipResponse.of(s, true);
    }

    /**
     * CONFIRMED → EXPORTED (8.5#2): trừ tồn thực theo hàng đang giữ, ghi giá vốn + hạn bảo hành, phát
     * export.exported. Khóa phiếu + Idempotency-Key cố định → bấm lại / gọi lại chỉ trừ tồn 1 lần.
     */
    public ExportSlipResponse export(Integer id) {
        ExportSlip s = lock(id);
        if (s.getStatus() != ExportSlip.Status.CONFIRMED) {
            throw new BusinessException(ErrorCode.CONFLICT, "Chỉ xuất được phiếu đã xác nhận");
        }
        JsonNode data = downstream(() -> inventory.commit(key(id, "export"),
                new InventoryClient.CommitRequest(REF_TYPE, id, currentUserId(), "EXPORT"))).path("data");
        Map<Integer, BigDecimal> unitCost = new HashMap<>();
        for (JsonNode c : data.path("unitCosts")) {
            unitCost.put(c.path("productId").asInt(), c.path("unitCost").decimalValue());
        }
        LocalDateTime now = LocalDateTime.now();
        for (ExportSlipItem i : s.getItems()) {
            i.setUnitCost(unitCost.get(i.getProductId()));
            if (i.getWarrantyMonths() != null && i.getWarrantyMonths() > 0) {
                i.setWarrantyUntil(now.toLocalDate().plusMonths(i.getWarrantyMonths()));
            }
        }
        s.setStatus(ExportSlip.Status.EXPORTED);
        s.setExportDate(now);
        s.setExportedBy(currentUserId());
        outbox.append(EVENT_EXPORTED, REF_TYPE, id, exportedPayload(s));
        return ExportSlipResponse.of(s, true);
    }

    /** Hủy trước khi xuất; nhả hàng đang giữ. */
    public ExportSlipResponse cancel(Integer id) {
        ExportSlip s = lock(id);
        requireNotExported(s, "hủy");
        if (s.isStockReserved()) {
            release(id, "cancel");
        }
        s.setStockReserved(false);
        s.setStatus(ExportSlip.Status.CANCELLED);
        return ExportSlipResponse.of(s, true);
    }

    // ------------------------------------------------------------------ giữ hàng

    /**
     * Giữ đủ mọi dòng tại kho xuất. Thiếu hàng (409 STOCK_NOT_ENOUGH) hoặc serial không nằm sẵn ở kho xuất không phải
     * lỗi: phiếu vẫn lưu, chưa giữ, trả dòng thiếu + gợi ý kho khác + serial cần chuyển kho để tách phiếu.
     * Lỗi khác (kho ngừng hoạt động, phiếu đã giữ…) → báo lỗi.
     *
     * @return null nếu giữ đủ; ngược lại dòng thiếu / serial không có sẵn
     */
    ExportSlipResponse.ReserveOutcome reserve(ExportSlip s) {
        List<String> unavailableSerials = List.of();
        try {
            downstream(() -> inventory.reserve(key(s.getId(), "reserve"), reserveRequest(s)));
            s.setStockReserved(true);
            return null;
        } catch (BusinessException ex) {
            if (ex.getCode() == ErrorCode.STOCK_NOT_ENOUGH) {
                // thiếu số lượng – xem gợi ý bên dưới
            } else if (ex.getCode() == ErrorCode.CONFLICT && ex.getDetails() instanceof Map<?, ?> d
                    && d.get("serials") instanceof List<?> serials) {
                unavailableSerials = serials.stream().map(String::valueOf).toList();
            } else {
                throw ex;
            }
        }
        s.setStockReserved(false);
        Map<Integer, Integer> qty = new LinkedHashMap<>();
        s.getItems().forEach(i -> qty.merge(i.getProductId(), i.getQty(), Integer::sum));
        List<ExportSlipResponse.Shortage> result = new ArrayList<>();
        for (JsonNode l : check(s.getWarehouseId(), qty).path("lines")) {
            if (l.path("shortage").asInt() <= 0) {
                continue;
            }
            List<ExportSlipResponse.OtherWarehouse> others = new ArrayList<>();
            for (JsonNode o : l.path("otherWarehouses")) {
                others.add(new ExportSlipResponse.OtherWarehouse(o.path("warehouseId").asInt(),
                        o.path("warehouseName").asText(null), o.path("available").asInt()));
            }
            result.add(new ExportSlipResponse.Shortage(l.path("productId").asInt(), l.path("requested").asInt(),
                    l.path("available").asInt(), l.path("shortage").asInt(), others));
        }
        return new ExportSlipResponse.ReserveOutcome(result, unavailableSerials);
    }

    private void release(Integer id, String reason) {
        downstream(() -> inventory.release(key(id, reason), new InventoryClient.ReleaseRequest(REF_TYPE, id)));
    }

    private JsonNode check(Integer warehouseId, Map<Integer, Integer> qtyByProduct) {
        List<InventoryClient.CheckItem> items = qtyByProduct.entrySet().stream()
                .map(e -> new InventoryClient.CheckItem(e.getKey(), e.getValue())).toList();
        return downstream(() -> inventory.check(new InventoryClient.CheckRequest(warehouseId, items))).path("data");
    }

    private InventoryClient.ReserveRequest reserveRequest(ExportSlip s) {
        return new InventoryClient.ReserveRequest(s.getWarehouseId(), REF_TYPE, s.getId(), currentUserId(),
                s.getCustomerId(), null, s.getItems().stream()
                        .map(i -> new InventoryClient.ReserveLine(i.getProductId(), i.getQty(), i.getSerials()))
                        .toList());
    }

    /** Mỗi lần gọi một khóa riêng (phiếu đã khóa nên không chạy chồng); riêng "export" cố định để gọi lại an toàn. */
    private static String key(Integer id, String action) {
        String base = REF_TYPE + ":" + id + ":" + action;
        return action.equals("export") ? base : base + ":" + UUID.randomUUID();
    }

    // ------------------------------------------------------------------ dữ liệu phiếu

    /** Ghi thông tin phiếu + thay toàn bộ dòng; snapshot sản phẩm / khách lấy từ catalog, kiểm quy tắc serial. */
    void apply(ExportSlip s, ExportSlipRequest req) {
        ExportSlip.ExportType type = req.exportType() != null ? req.exportType() : ExportSlip.ExportType.SALE;
        if (type == ExportSlip.ExportType.SALE && req.customerId() == null) {
            throw invalid("customerId", "Phiếu bán hàng phải chọn khách hàng");
        }
        Map<Integer, JsonNode> products = loadProducts(req.lines());
        checkSerials(req.lines(), products);
        s.setCustomerSnapshot(req.customerId() == null ? null : loadCustomer(req.customerId()));

        s.setWarehouseId(req.warehouseId());
        s.setExportType(type);
        s.setCustomerId(req.customerId());
        s.setDeliveryMethod(req.deliveryMethod());
        s.setTrackingNo(trimToNull(req.trackingNo()));
        s.setVatRate(req.vatRate() != null ? req.vatRate() : VatRate.NONE);
        s.setDepositAmount(req.depositAmount() != null ? req.depositAmount() : BigDecimal.ZERO);
        s.setDueDate(req.dueDate());
        s.setSalesUserId(req.salesUserId() != null ? req.salesUserId() : currentUserId());
        s.setNote(trimToNull(req.note()));

        s.getItems().clear();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ExportSlipRequest.Line l : req.lines()) {
            JsonNode p = products.get(l.productId());
            BigDecimal price = l.unitPrice();
            if (price == null) {
                if (!p.hasNonNull("defaultPriceOut")) {
                    throw invalid("lines", "Sản phẩm " + p.path("code").asText() + " chưa có giá bán, vui lòng nhập đơn giá");
                }
                price = p.path("defaultPriceOut").decimalValue();
            }
            ExportSlipItem i = new ExportSlipItem();
            i.setSlip(s);
            i.setProductId(l.productId());
            i.setProductSnapshot(snapshot(p));
            i.setQty(l.qty());
            i.setUnitPrice(price);
            i.setLineTotal(price.multiply(BigDecimal.valueOf(l.qty())));
            i.setSerials(l.serials() == null || l.serials().isEmpty() ? null
                    : l.serials().stream().map(String::trim).toList());
            i.setWarrantyMonths(p.hasNonNull("warrantyMonths") ? p.path("warrantyMonths").asInt() : null);
            s.getItems().add(i);
            subtotal = subtotal.add(i.getLineTotal());
        }
        recalcTotals(s, subtotal);
    }

    static void recalcTotals(ExportSlip s, BigDecimal subtotal) {
        BigDecimal vat = subtotal.multiply(s.getVatRate().percent()).divide(BigDecimal.valueOf(100), 2,
                RoundingMode.HALF_UP);
        s.setSubtotal(subtotal);
        s.setVatAmount(vat);
        s.setTotalAmount(subtotal.add(vat));
    }

    /** Sản phẩm (1 lần gọi catalog cho cả phiếu); không có hoặc đã xóa mềm → 400. */
    Map<Integer, JsonNode> loadProducts(List<ExportSlipRequest.Line> lines) {
        List<Integer> ids = lines.stream().map(ExportSlipRequest.Line::productId).distinct().toList();
        JsonNode data = downstream(() -> catalog.products(new CatalogClient.ProductIds(ids))).path("data");
        Map<Integer, JsonNode> result = new LinkedHashMap<>();
        for (JsonNode p : data.path("products")) {
            if (!p.path("deleted").asBoolean()) {
                result.put(p.path("id").asInt(), p);
            }
        }
        for (Integer id : ids) {
            if (!result.containsKey(id)) {
                throw invalid("lines", "Sản phẩm " + id + " không tồn tại");
            }
        }
        return result;
    }

    private Map<String, Object> loadCustomer(Integer customerId) {
        JsonNode found = downstream(() -> catalog.partners(new CatalogClient.PartnerIds(null, List.of(customerId))))
                .path("data").path("customers");
        for (JsonNode c : found) {
            if (c.path("id").asInt() == customerId && !c.path("deleted").asBoolean()) {
                Map<String, Object> m = new LinkedHashMap<>();
                for (String f : List.of("code", "name", "customerType", "taxCode", "address", "phone", "email")) {
                    if (c.hasNonNull(f)) {
                        m.put(f, c.path(f).asText());
                    }
                }
                return m;
            }
        }
        throw invalid("customerId", "Khách hàng " + customerId + " không tồn tại");
    }

    private static Map<String, Object> snapshot(JsonNode p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", p.path("code").asText());
        m.put("name", p.path("name").asText());
        m.put("unit", p.path("unit").asText());
        m.put("hasSerial", p.path("hasSerial").asBoolean());
        return m;
    }

    /** Sản phẩm có serial: số serial = số lượng; không serial: không nhận serial; serial không lặp trong phiếu. */
    static void checkSerials(List<ExportSlipRequest.Line> lines, Map<Integer, JsonNode> products) {
        Set<String> seen = new HashSet<>();
        for (ExportSlipRequest.Line l : lines) {
            JsonNode p = products.get(l.productId());
            int count = l.serials() == null ? 0 : l.serials().size();
            if (p.path("hasSerial").asBoolean() && count != l.qty()) {
                throw invalid("serials", "Sản phẩm " + p.path("code").asText() + " có serial: cần đúng " + l.qty()
                        + " serial");
            }
            if (!p.path("hasSerial").asBoolean() && count > 0) {
                throw invalid("serials", "Sản phẩm " + p.path("code").asText() + " không quản lý serial");
            }
            if (l.serials() != null) {
                for (String serial : l.serials()) {
                    if (!seen.add(serial.trim())) {
                        throw invalid("serials", "Serial bị lặp trong phiếu: " + serial.trim());
                    }
                }
            }
        }
    }

    private Map<String, Object> exportedPayload(ExportSlip s) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("slipId", s.getId());
        p.put("code", s.getCode());
        p.put("exportType", s.getExportType().name());
        p.put("warehouseId", s.getWarehouseId());
        p.put("customerId", s.getCustomerId());
        p.put("salesUserId", s.getSalesUserId());
        p.put("exportDate", s.getExportDate().toString());
        p.put("subtotal", s.getSubtotal());
        p.put("vatAmount", s.getVatAmount());
        p.put("totalAmount", s.getTotalAmount());
        p.put("depositAmount", s.getDepositAmount());
        p.put("dueDate", s.getDueDate() == null ? null : s.getDueDate().toString());
        p.put("lines", s.getItems().stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productId", i.getProductId());
            m.put("qty", i.getQty());
            m.put("unitPrice", i.getUnitPrice());
            m.put("unitCost", i.getUnitCost());
            m.put("serials", i.getSerials());
            m.put("warrantyUntil", i.getWarrantyUntil() == null ? null : i.getWarrantyUntil().toString());
            return m;
        }).toList());
        return p;
    }

    // ------------------------------------------------------------------ tiện ích

    ExportSlip lock(Integer id) {
        ExportSlip s = slips.lock(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy phiếu xuất"));
        checkAccess(s.getWarehouseId());
        return s;
    }

    static void requireNotExported(ExportSlip s, String action) {
        if (s.getStatus() == ExportSlip.Status.EXPORTED || s.getStatus() == ExportSlip.Status.CANCELLED) {
            throw new BusinessException(ErrorCode.CONFLICT, "Phiếu đã " + (s.getStatus() == ExportSlip.Status.EXPORTED
                    ? "xuất" : "hủy") + ", không " + action + " được");
        }
    }

    /** PXK-yyyy-nnnn; INSERT … ON DUPLICATE KEY UPDATE khóa dòng sequence tới hết transaction. */
    String nextCode(int year) {
        slips.nextSequence(PREFIX, year);
        return "%s-%d-%04d".formatted(PREFIX, year, slips.currentSequence(PREFIX, year));
    }

    /** Lỗi gọi sang service khác: lỗi nghiệp vụ giữ nguyên mã; mất kết nối / quá tải → 503. */
    static <T> T downstream(Supplier<T> call) {
        try {
            return call.get();
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Không gọi được dịch vụ liên quan, thử lại sau");
        }
    }

    static void checkAccess(Integer warehouseId) {
        if (!UserContextHolder.get().canAccessWarehouse(warehouseId.longValue())) {
            throw new BusinessException(ErrorCode.WAREHOUSE_FORBIDDEN);
        }
    }

    static Integer currentUserId() {
        return UserContextHolder.get().userId().intValue();
    }

    static BusinessException invalid(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(),
                Map.of(field, message));
    }

    /** "%abc%" chữ thường cho LIKE … ESCAPE '!'; thoát %, _ để tìm đúng chuỗi đã gõ. */
    static String likePattern(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String escaped = keyword.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    private static String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
