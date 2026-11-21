package com.microinvent.procurement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microinvent.common.context.UserContext;
import com.microinvent.common.context.UserContextHolder;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.procurement.client.CatalogClient;
import com.microinvent.procurement.client.InventoryClient;
import com.microinvent.procurement.domain.GoodsReceipt;
import com.microinvent.procurement.domain.OutboxEvent;
import com.microinvent.procurement.domain.VatRate;
import com.microinvent.procurement.repository.GoodsReceiptRepository;
import com.microinvent.procurement.repository.OutboxEventRepository;
import com.microinvent.procurement.web.dto.ReceiptRequest;
import com.microinvent.procurement.web.dto.ReceiptResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class ReceiptServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UserContext STOREKEEPER =
            new UserContext(4L, "thukho", Set.of("STOREKEEPER"), Set.of(1L), false, 1L);

    private GoodsReceiptRepository receipts;
    private OutboxEventRepository outbox;
    private InventoryClient inventory;
    private CatalogClient catalog;
    private ReceiptService service;
    /** Phiếu được lưu gần nhất (giả lập bảng goods_receipts). */
    private GoodsReceipt saved;

    @BeforeEach
    void setUp() {
        receipts = mock(GoodsReceiptRepository.class);
        outbox = mock(OutboxEventRepository.class);
        inventory = mock(InventoryClient.class);
        catalog = mock(CatalogClient.class);
        service = new ReceiptService(receipts, outbox, inventory, catalog);
        UserContextHolder.set(STOREKEEPER);
        when(receipts.save(any(GoodsReceipt.class))).thenAnswer(inv -> {
            saved = inv.getArgument(0);
            saved.setId(7);
            return saved;
        });
        when(receipts.currentSequence("PNK", LocalDate.now().getYear())).thenReturn(12);
        product(1, "ISR4331", true, false);
        product(7, "CAT6", false, false);
        // Catalog giả: trả các SP có trong `catalogProducts`, id còn lại vào notFound.
        when(catalog.products(any())).thenAnswer(inv -> {
            List<Integer> ids = inv.<CatalogClient.ProductIds>getArgument(0).ids();
            return JSON.valueToTree(Map.of("success", true, "data", Map.of(
                    "products", ids.stream().filter(catalogProducts::containsKey).map(catalogProducts::get).toList(),
                    "notFound", ids.stream().filter(i -> !catalogProducts.containsKey(i)).toList())));
        });
        when(catalog.partners(any())).thenReturn(JSON.valueToTree(Map.of("data",
                Map.of("suppliers", List.of(), "customers", List.of()))));
        supplier(3, false);
    }

    /** Sản phẩm trong catalog giả theo id. */
    private final Map<Integer, Map<String, Object>> catalogProducts = new java.util.HashMap<>();

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void createDraftWithSnapshotsTotalsAndCode() {
        ReceiptResponse r = service.create(request(VatRate.V10, line(1, 2, "1000", List.of("SN-1", "SN-2")),
                line(7, 10, "50", null)));

        assertThat(r.code()).isEqualTo("PNK-" + LocalDate.now().getYear() + "-0012");
        assertThat(r.status()).isEqualTo(GoodsReceipt.Status.DRAFT);
        assertThat(r.subtotal()).isEqualByComparingTo("2500");
        assertThat(r.vatAmount()).isEqualByComparingTo("250.00");
        assertThat(r.totalAmount()).isEqualByComparingTo("2750.00");
        assertThat(r.lines().get(0).product()).containsEntry("code", "ISR4331").containsEntry("hasSerial", true);
        assertThat(r.createdBy()).isEqualTo(4);
        verifyNoInteractions(inventory);
    }

    @Test
    void createValidatesSerialsSupplierProductAndWarehouse() {
        assertThatThrownBy(() -> service.create(request(VatRate.NONE, line(1, 2, "1", List.of("SN-1")))))
                .extracting("code").isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThatThrownBy(() -> service.create(request(VatRate.NONE, line(7, 1, "1", List.of("X")))))
                .extracting("code").isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThatThrownBy(() -> service.create(request(VatRate.NONE, line(1, 2, "1", List.of("SN-1", "SN-1")))))
                .extracting("code").isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThatThrownBy(() -> service.create(new ReceiptRequest(1, null, null, null, null, null, null, null, null,
                null, List.of(line(7, 1, "1", null))))).extracting("code").isEqualTo(ErrorCode.VALIDATION_ERROR);

        assertThatThrownBy(() -> service.create(request(VatRate.NONE, line(99, 1, "1", null))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getDetails())
                        .isEqualTo(Map.of("lines", "Sản phẩm 99 không tồn tại")));

        assertThatThrownBy(() -> service.create(new ReceiptRequest(2, null, 3, null, null, null, null, null, null,
                null, List.of(line(7, 1, "1", null))))).extracting("code").isEqualTo(ErrorCode.WAREHOUSE_FORBIDDEN);
        verify(receipts, never()).save(any());
    }

    @Test
    void createRejectsUnknownOrDeletedSupplierProductAndCustomer() {
        assertThatThrownBy(() -> service.create(new ReceiptRequest(1, null, 999, null, null, null, null, null, null,
                null, List.of(line(7, 1, "1", null)))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getDetails())
                        .isEqualTo(Map.of("supplierId", "Nhà cung cấp 999 không tồn tại")));

        supplier(5, true);
        assertThatThrownBy(() -> service.create(new ReceiptRequest(1, null, 5, null, null, null, null, null, null,
                null, List.of(line(7, 1, "1", null))))).extracting("code").isEqualTo(ErrorCode.VALIDATION_ERROR);

        product(8, "OLD", false, true);
        assertThatThrownBy(() -> service.create(request(VatRate.NONE, line(8, 1, "1", null))))
                .satisfies(ex -> assertThat(((BusinessException) ex).getDetails())
                        .isEqualTo(Map.of("lines", "Sản phẩm 8 không tồn tại")));

        when(catalog.partners(new CatalogClient.PartnerIds(null, List.of(42))))
                .thenReturn(JSON.valueToTree(Map.of("data", Map.of("customers", List.of()))));
        assertThatThrownBy(() -> service.create(new ReceiptRequest(1, GoodsReceipt.SourceType.RETAIL_CUSTOMER, null,
                42, null, null, null, null, null, null, List.of(line(7, 1, "1", null)))))
                .satisfies(ex -> assertThat(((BusinessException) ex).getDetails())
                        .isEqualTo(Map.of("customerId", "Khách hàng 42 không tồn tại")));
        verify(receipts, never()).save(any());
    }

    @Test
    void createLoadsAllProductsInOneCatalogCall() {
        service.create(request(VatRate.NONE, line(1, 1, "1", List.of("SN-1")), line(7, 1, "1", null),
                line(7, 2, "1", null)));

        verify(catalog).products(new CatalogClient.ProductIds(List.of(1, 7)));
    }

    @Test
    void confirmReceivesStockOnceAndWritesOutbox() {
        GoodsReceipt draft = draft();
        when(receipts.lock(7)).thenReturn(Optional.of(draft));

        service.confirm(7);

        ArgumentCaptor<InventoryClient.StockRequest> req = ArgumentCaptor.forClass(InventoryClient.StockRequest.class);
        verify(inventory).receive(eq("RECEIPT:7:confirm"), req.capture());
        assertThat(req.getValue().refType()).isEqualTo("RECEIPT");
        assertThat(req.getValue().lines()).extracting(InventoryClient.Line::serials)
                .containsExactly(List.of("SN-1", "SN-2"), null);
        assertThat(draft.getStatus()).isEqualTo(GoodsReceipt.Status.CONFIRMED);
        ArgumentCaptor<OutboxEvent> ev = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(ev.capture());
        assertThat(ev.getValue().getEventType()).isEqualTo("receipt.confirmed");
        assertThat(ev.getValue().getEventId()).hasSize(36);

        assertThatThrownBy(() -> service.confirm(7)).extracting("code").isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void confirmFailureFromInventoryKeepsDraftAndNoEvent() {
        GoodsReceipt draft = draft();
        when(receipts.lock(7)).thenReturn(Optional.of(draft));
        when(inventory.receive(anyString(), any()))
                .thenThrow(new BusinessException(ErrorCode.CONFLICT, "Serial đã tồn tại trong hệ thống"));

        assertThatThrownBy(() -> service.confirm(7)).extracting("code").isEqualTo(ErrorCode.CONFLICT);
        assertThat(draft.getStatus()).isEqualTo(GoodsReceipt.Status.DRAFT);
        verify(outbox, never()).save(any());
    }

    @Test
    void downstreamOutageBecomes503() {
        GoodsReceipt draft = draft();
        when(receipts.lock(7)).thenReturn(Optional.of(draft));
        when(inventory.receive(anyString(), any())).thenThrow(new IllegalStateException("connection refused"));

        assertThatThrownBy(() -> service.confirm(7)).extracting("code").isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    void cancelConfirmedReversesStockButDraftDoesNot() {
        GoodsReceipt confirmed = draft();
        confirmed.setStatus(GoodsReceipt.Status.CONFIRMED);
        when(receipts.lock(7)).thenReturn(Optional.of(confirmed));
        service.cancel(7);
        verify(inventory).reverse(eq("RECEIPT:7:cancel"), any());
        assertThat(confirmed.getStatus()).isEqualTo(GoodsReceipt.Status.CANCELLED);
        assertThatThrownBy(() -> service.cancel(7)).extracting("code").isEqualTo(ErrorCode.CONFLICT);

        GoodsReceipt d = draft();
        d.setId(8);
        when(receipts.lock(8)).thenReturn(Optional.of(d));
        service.cancel(8);
        verify(inventory, never()).reverse(startsWith("RECEIPT:8"), any());
    }

    @Test
    void keywordEscapesLikeWildcards() {
        assertThat(ReceiptService.likePattern(" PNK_2026% ")).isEqualTo("%pnk!_2026!%%");
        assertThat(ReceiptService.likePattern("a!b")).isEqualTo("%a!!b%");
        assertThat(ReceiptService.likePattern(" ")).isNull();
    }

    @Test
    void updateConfirmedReversesOldThenReceivesNewAndRestoresOnFailure() {
        GoodsReceipt confirmed = draft();
        confirmed.setStatus(GoodsReceipt.Status.CONFIRMED);
        when(receipts.lock(7)).thenReturn(Optional.of(confirmed));
        when(inventory.receive(anyString(), any())).thenAnswer(inv -> {
            if (inv.<String>getArgument(0).endsWith(":receive")) {
                throw new BusinessException(ErrorCode.CONFLICT, "Serial đã tồn tại trong hệ thống");
            }
            return null;
        });

        assertThatThrownBy(() -> service.update(7, request(VatRate.NONE, line(7, 3, "50", null))))
                .extracting("code").isEqualTo(ErrorCode.CONFLICT);

        InOrder order = inOrder(inventory);
        ArgumentCaptor<InventoryClient.StockRequest> reversed = ArgumentCaptor.forClass(InventoryClient.StockRequest.class);
        ArgumentCaptor<InventoryClient.StockRequest> restored = ArgumentCaptor.forClass(InventoryClient.StockRequest.class);
        order.verify(inventory).reverse(endsWith(":reverse"), reversed.capture());
        order.verify(inventory).receive(endsWith(":receive"), any());
        order.verify(inventory).receive(endsWith(":restore"), restored.capture());
        assertThat(restored.getValue()).isEqualTo(reversed.getValue());
        assertThat(reversed.getValue().lines()).hasSize(2);
    }

    /** Phiếu nháp id 7: SP1 x2 có serial, SP7 x10 – tạo qua chính service.create(). */
    private GoodsReceipt draft() {
        service.create(request(VatRate.NONE, line(1, 2, "1000", List.of("SN-1", "SN-2")), line(7, 10, "50", null)));
        return saved;
    }

    private void product(int id, String code, boolean hasSerial, boolean deleted) {
        catalogProducts.put(id, Map.of("id", id, "code", code, "name", "SP " + code, "unit", "cái",
                "hasSerial", hasSerial, "deleted", deleted));
    }

    private void supplier(int id, boolean deleted) {
        when(catalog.partners(new CatalogClient.PartnerIds(List.of(id), null))).thenReturn(JSON.valueToTree(Map.of(
                "data", Map.of("suppliers", List.of(Map.of("id", id, "code", "NCC" + id, "deleted", deleted))))));
    }

    private static ReceiptRequest request(VatRate vat, ReceiptRequest.Line... lines) {
        return new ReceiptRequest(1, null, 3, null, null, "HĐ-01", null, null, vat, null, List.of(lines));
    }

    private static ReceiptRequest.Line line(int productId, int qty, String price, List<String> serials) {
        return new ReceiptRequest.Line(productId, qty, new BigDecimal(price), serials, null);
    }
}
