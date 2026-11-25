package com.microinvent.sales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microinvent.common.context.UserContext;
import com.microinvent.common.context.UserContextHolder;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.sales.client.CatalogClient;
import com.microinvent.sales.client.InventoryClient;
import com.microinvent.sales.domain.ExportSlip;
import com.microinvent.sales.domain.VatRate;
import com.microinvent.sales.repository.ExportSlipRepository;
import com.microinvent.sales.web.dto.ExportSlipRequest;
import com.microinvent.sales.web.dto.ExportSlipResponse;
import com.microinvent.tenant.outbox.OutboxWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class ExportSlipServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UserContext STOREKEEPER =
            new UserContext(4L, "thukho", Set.of("STOREKEEPER"), Set.of(1L), false, 1L);

    private ExportSlipRepository slips;
    private InventoryClient inventory;
    private CatalogClient catalog;
    private OutboxWriter outbox;
    private ExportSlipService service;
    private ExportSlip saved;
    private int nextId = 9;
    private final List<ExportSlip> children = new java.util.ArrayList<>();
    private final Map<Integer, Map<String, Object>> products = new HashMap<>();
    private static final UserContext MANAGER =
            new UserContext(2L, "manager", Set.of("MANAGER"), Set.of(), true, null);

    @BeforeEach
    void setUp() {
        slips = mock(ExportSlipRepository.class);
        inventory = mock(InventoryClient.class);
        catalog = mock(CatalogClient.class);
        outbox = mock(OutboxWriter.class);
        service = new ExportSlipService(slips, inventory, catalog, outbox);
        UserContextHolder.set(STOREKEEPER);
        when(slips.saveAndFlush(any(ExportSlip.class))).thenAnswer(inv -> {
            ExportSlip s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(nextId++);
            }
            if (s.getSplitFromId() == null) {
                saved = s;
            } else {
                children.add(s);
            }
            return s;
        });
        when(slips.currentSequence("PXK", LocalDate.now().getYear())).thenReturn(3);
        product(1, "LAPTOP", true, 12, "15000000");
        product(7, "CAT6", false, null, "50");
        when(catalog.products(any())).thenAnswer(inv -> {
            List<Integer> ids = inv.<CatalogClient.ProductIds>getArgument(0).ids();
            return JSON.valueToTree(Map.of("data", Map.of(
                    "products", ids.stream().filter(products::containsKey).map(products::get).toList())));
        });
        when(catalog.partners(any())).thenReturn(JSON.valueToTree(Map.of("data", Map.of("customers", List.of()))));
        when(catalog.partners(new CatalogClient.PartnerIds(null, List.of(5)))).thenReturn(JSON.valueToTree(Map.of(
                "data", Map.of("customers", List.of(Map.of("id", 5, "code", "KH5", "name", "Công ty A",
                        "deleted", false))))));
        when(inventory.check(any())).thenReturn(JSON.valueToTree(Map.of("data", Map.of("enough", false, "lines",
                List.of(Map.of("productId", 7, "requested", 10, "available", 4, "shortage", 6,
                        "otherWarehouses", List.of(Map.of("warehouseId", 2, "warehouseName", "HCM01",
                                "available", 30))))))));
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void createReservesAtExportWarehouseWithSnapshotsAndTotals() {
        ExportSlipResponse r = service.create(request(VatRate.V10, line(1, 1, null, List.of("SN-1")),
                line(7, 10, "60", null)));

        assertThat(r.code()).isEqualTo("PXK-" + LocalDate.now().getYear() + "-0003");
        assertThat(r.status()).isEqualTo(ExportSlip.Status.PENDING);
        assertThat(r.stockReserved()).isTrue();
        assertThat(r.shortages()).isNull();
        assertThat(r.subtotal()).isEqualByComparingTo("15000600"); // giá bán mặc định + giá nhập tay
        assertThat(r.totalAmount()).isEqualByComparingTo("16500660.00");
        assertThat(r.customer()).containsEntry("name", "Công ty A");
        assertThat(r.lines().get(0).warrantyMonths()).isEqualTo(12);
        ArgumentCaptor<InventoryClient.ReserveRequest> req = ArgumentCaptor.forClass(InventoryClient.ReserveRequest.class);
        verify(inventory).reserve(startsWith("EXPORT_SLIP:9:reserve:"), req.capture());
        assertThat(req.getValue().refType()).isEqualTo("EXPORT_SLIP");
        assertThat(req.getValue().lines()).extracting(InventoryClient.ReserveLine::serials)
                .containsExactly(List.of("SN-1"), null);
    }

    @Test
    void createWhenShortKeepsSlipUnreservedAndReturnsShortages() {
        when(inventory.reserve(anyString(), any()))
                .thenThrow(new BusinessException(ErrorCode.STOCK_NOT_ENOUGH, "Không đủ hàng"));

        ExportSlipResponse r = service.create(request(VatRate.NONE, line(7, 10, "50", null)));

        assertThat(r.stockReserved()).isFalse();
        assertThat(r.shortages()).singleElement().satisfies(s -> {
            assertThat(List.of(s.productId(), s.requested(), s.available(), s.shortage())).containsExactly(7, 10, 4, 6);
            assertThat(s.otherWarehouses()).extracting(ExportSlipResponse.OtherWarehouse::warehouseId)
                    .containsExactly(2);
        });
        assertThatThrownBy(() -> {
            when(slips.lock(9)).thenReturn(Optional.of(saved));
            service.confirm(9);
        }).extracting("code").isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void createValidatesCustomerProductsSerialsAndWarehouse() {
        assertThatThrownBy(() -> service.create(new ExportSlipRequest(1, null, null, null, null, null, null, null,
                null, null, List.of(line(7, 1, "1", null))))).extracting("code").isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThatThrownBy(() -> service.create(new ExportSlipRequest(1, null, 99, null, null, null, null, null,
                null, null, List.of(line(7, 1, "1", null)))))
                .satisfies(ex -> assertThat(((BusinessException) ex).getDetails())
                        .isEqualTo(Map.of("customerId", "Khách hàng 99 không tồn tại")));
        assertThatThrownBy(() -> service.create(request(VatRate.NONE, line(1, 2, null, List.of("SN-1")))))
                .extracting("code").isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThatThrownBy(() -> service.create(request(VatRate.NONE, line(7, 1, null, List.of("X")))))
                .extracting("code").isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThatThrownBy(() -> service.create(request(VatRate.NONE, line(88, 1, "1", null))))
                .satisfies(ex -> assertThat(((BusinessException) ex).getDetails())
                        .isEqualTo(Map.of("lines", "Sản phẩm 88 không tồn tại")));
        assertThatThrownBy(() -> service.create(new ExportSlipRequest(2, null, 5, null, null, null, null, null,
                null, null, List.of(line(7, 1, "1", null))))).extracting("code").isEqualTo(ErrorCode.WAREHOUSE_FORBIDDEN);
        // Phiếu xuất hàng hỏng / khác không bắt buộc khách.
        service.create(new ExportSlipRequest(1, ExportSlip.ExportType.DAMAGED, null, null, null, null, null, null,
                null, null, List.of(line(7, 1, "1", null))));
        verify(slips).saveAndFlush(any());
    }

    @Test
    void exportCommitsOnceWithFixedKeyAndWritesCostWarrantyAndEvent() {
        ExportSlip s = confirmedSlip();
        when(inventory.commit(anyString(), any())).thenReturn(JSON.valueToTree(Map.of("data", Map.of("unitCosts",
                List.of(Map.of("productId", 1, "unitCost", 12000000), Map.of("productId", 7, "unitCost", 30))))));

        ExportSlipResponse r = service.export(9);

        verify(inventory).commit(eq("EXPORT_SLIP:9:export"), eq(new InventoryClient.CommitRequest("EXPORT_SLIP", 9, 4,
                "EXPORT")));
        assertThat(r.status()).isEqualTo(ExportSlip.Status.EXPORTED);
        assertThat(r.exportedBy()).isEqualTo(4);
        assertThat(r.lines().get(0).unitCost()).isEqualByComparingTo("12000000");
        assertThat(r.lines().get(0).warrantyUntil()).isEqualTo(LocalDate.now().plusMonths(12));
        assertThat(r.lines().get(1).warrantyUntil()).isNull();
        verify(outbox).append(eq("export.exported"), eq("EXPORT_SLIP"), eq(9), anyMap());

        assertThatThrownBy(() -> service.export(9)).extracting("code").isEqualTo(ErrorCode.CONFLICT);
        assertThatThrownBy(() -> service.cancel(9)).extracting("code").isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void exportFailureKeepsConfirmedAndNoEvent() {
        ExportSlip s = confirmedSlip();
        when(inventory.commit(anyString(), any())).thenThrow(new IllegalStateException("connection refused"));

        assertThatThrownBy(() -> service.export(9)).extracting("code").isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
        assertThat(s.getStatus()).isEqualTo(ExportSlip.Status.CONFIRMED);
        verify(outbox, never()).append(anyString(), anyString(), any(), anyMap());
    }

    @Test
    void confirmOnlyFromPendingAndCancelReleasesHolds() {
        service.create(request(VatRate.NONE, line(7, 2, "50", null)));
        when(slips.lock(9)).thenReturn(Optional.of(saved));
        assertThatThrownBy(() -> service.export(9)).extracting("code").isEqualTo(ErrorCode.CONFLICT);

        service.confirm(9);
        assertThat(saved.getConfirmedBy()).isEqualTo(4);
        assertThatThrownBy(() -> service.confirm(9)).extracting("code").isEqualTo(ErrorCode.CONFLICT);

        service.cancel(9);
        verify(inventory).release(startsWith("EXPORT_SLIP:9:cancel:"),
                eq(new InventoryClient.ReleaseRequest("EXPORT_SLIP", 9)));
        assertThat(saved.getStatus()).isEqualTo(ExportSlip.Status.CANCELLED);
        assertThat(saved.isStockReserved()).isFalse();
    }

    @Test
    void cancelUnreservedSlipDoesNotCallInventory() {
        when(inventory.reserve(anyString(), any()))
                .thenThrow(new BusinessException(ErrorCode.STOCK_NOT_ENOUGH, "Không đủ hàng"));
        service.create(request(VatRate.NONE, line(7, 10, "50", null)));
        when(slips.lock(9)).thenReturn(Optional.of(saved));

        service.cancel(9);

        verify(inventory, never()).release(anyString(), any());
    }

    @Test
    void updateReleasesThenReservesAgainAndRestoresOldHoldsOnFailure() {
        service.create(request(VatRate.NONE, line(7, 2, "50", null)));
        when(slips.lock(9)).thenReturn(Optional.of(saved));
        service.confirm(9);
        when(inventory.reserve(anyString(), any())).thenAnswer(inv -> {
            if (inv.<String>getArgument(0).contains(":reserve:")) {
                throw new BusinessException(ErrorCode.CONFLICT, "Serial không có sẵn tại kho để giữ");
            }
            return null;
        });

        assertThatThrownBy(() -> service.update(9, request(VatRate.NONE, line(7, 5, "50", null))))
                .extracting("code").isEqualTo(ErrorCode.CONFLICT);

        InOrder order = inOrder(inventory);
        order.verify(inventory).release(startsWith("EXPORT_SLIP:9:edit:"), any());
        order.verify(inventory).reserve(startsWith("EXPORT_SLIP:9:reserve:"), any());
        ArgumentCaptor<InventoryClient.ReserveRequest> restored =
                ArgumentCaptor.forClass(InventoryClient.ReserveRequest.class);
        order.verify(inventory).reserve(startsWith("EXPORT_SLIP:9:restore:"), restored.capture());
        assertThat(restored.getValue().lines()).singleElement()
                .extracting(InventoryClient.ReserveLine::qty).isEqualTo(2);
    }

    @Test
    void updateConfirmedGoesBackToPending() {
        service.create(request(VatRate.NONE, line(7, 2, "50", null)));
        when(slips.lock(9)).thenReturn(Optional.of(saved));
        service.confirm(9);

        ExportSlipResponse r = service.update(9, request(VatRate.NONE, line(7, 3, "50", null)));

        assertThat(r.status()).isEqualTo(ExportSlip.Status.PENDING);
        assertThat(r.confirmedBy()).isNull();
        assertThat(r.stockReserved()).isTrue();
        assertThat(r.totalAmount()).isEqualByComparingTo("150");
    }

    @Test
    void splitMovesShortQtyToChildSlipAtOtherWarehouseAndReservesBoth() {
        UserContextHolder.set(MANAGER);
        when(inventory.reserve(anyString(), any()))
                .thenThrow(new BusinessException(ErrorCode.STOCK_NOT_ENOUGH, "Không đủ hàng")).thenReturn(null);
        service.create(request(VatRate.V10, line(7, 10, "50", null)));
        when(slips.lock(9)).thenReturn(Optional.of(saved));

        ExportSlipResponse r = service.split(9, split(2, splitLine(7, 6, null)));

        assertThat(r.stockReserved()).isTrue();
        assertThat(r.lines()).singleElement().satisfies(l -> assertThat(l.qty()).isEqualTo(4));
        assertThat(r.subtotal()).isEqualByComparingTo("200");
        assertThat(r.splitSlips()).singleElement().satisfies(c -> {
            assertThat(c.warehouseId()).isEqualTo(2);
            assertThat(c.splitFromId()).isEqualTo(9);
            assertThat(c.stockReserved()).isTrue();
            assertThat(c.lines()).singleElement().satisfies(l -> assertThat(List.of(l.qty(), l.unitPrice().intValue()))
                    .containsExactly(6, 50));
            assertThat(c.totalAmount()).isEqualByComparingTo("330.00");
            assertThat(c.code()).startsWith("PXK-");
        });
        verify(inventory).reserve(startsWith("EXPORT_SLIP:10:reserve:"), any());
        verify(inventory, never()).release(anyString(), any()); // phiếu gốc chưa giữ → không cần nhả
    }

    @Test
    void splitMovesChosenSerialsWhenSerialIsInOtherWarehouse() {
        UserContextHolder.set(MANAGER);
        when(inventory.reserve(anyString(), any()))
                .thenThrow(new BusinessException(ErrorCode.CONFLICT, "Serial không có sẵn tại kho để giữ",
                        Map.of("serials", List.of("SN-2"))))
                .thenReturn(null);
        ExportSlipResponse created = service.create(request(VatRate.NONE, line(1, 2, "100", List.of("SN-1", "SN-2"))));
        assertThat(created.unavailableSerials()).containsExactly("SN-2");
        when(slips.lock(9)).thenReturn(Optional.of(saved));

        ExportSlipResponse r = service.split(9, split(2, splitLine(1, 1, List.of("SN-2"))));

        assertThat(r.lines()).singleElement().satisfies(l -> assertThat(l.serials()).containsExactly("SN-1"));
        assertThat(r.splitSlips().get(0).lines()).singleElement()
                .satisfies(l -> assertThat(l.serials()).containsExactly("SN-2"));
    }

    @Test
    void splitValidatesAllocationsAndStatus() {
        UserContextHolder.set(MANAGER);
        service.create(request(VatRate.NONE, line(7, 10, "50", null), line(1, 1, null, List.of("SN-1"))));
        when(slips.lock(9)).thenReturn(Optional.of(saved));

        assertThatThrownBy(() -> service.split(9, split(1, splitLine(7, 1, null)))).extracting("code")
                .isEqualTo(ErrorCode.VALIDATION_ERROR); // cùng kho xuất
        assertThatThrownBy(() -> service.split(9, split(2, splitLine(7, 11, null)))).extracting("code")
                .isEqualTo(ErrorCode.VALIDATION_ERROR); // nhiều hơn số trên phiếu
        assertThatThrownBy(() -> service.split(9, split(2, splitLine(1, 1, List.of("SN-9"))))).extracting("code")
                .isEqualTo(ErrorCode.VALIDATION_ERROR); // serial không thuộc phiếu
        assertThatThrownBy(() -> service.split(9, split(2, splitLine(7, 10, null), splitLine(1, 1, List.of("SN-1")))))
                .extracting("code").isEqualTo(ErrorCode.VALIDATION_ERROR); // tách hết phiếu
        service.confirm(9);
        assertThatThrownBy(() -> service.split(9, split(2, splitLine(7, 1, null)))).extracting("code")
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void splitFailureReleasesNewHoldsAndRestoresOriginalHolds() {
        UserContextHolder.set(MANAGER);
        service.create(request(VatRate.NONE, line(7, 10, "50", null))); // giữ đủ
        when(slips.lock(9)).thenReturn(Optional.of(saved));
        when(inventory.reserve(anyString(), any())).thenAnswer(inv -> {
            if (inv.<String>getArgument(0).startsWith("EXPORT_SLIP:10:reserve")) {
                throw new BusinessException(ErrorCode.CONFLICT, "Kho HCM01 không ở trạng thái hoạt động");
            }
            return null;
        });

        assertThatThrownBy(() -> service.split(9, split(2, splitLine(7, 3, null)))).extracting("code")
                .isEqualTo(ErrorCode.CONFLICT);

        verify(inventory).release(startsWith("EXPORT_SLIP:9:split:"), any());
        verify(inventory).release(startsWith("EXPORT_SLIP:9:rollback:"), any());
        ArgumentCaptor<InventoryClient.ReserveRequest> restored =
                ArgumentCaptor.forClass(InventoryClient.ReserveRequest.class);
        verify(inventory).reserve(startsWith("EXPORT_SLIP:9:restore:"), restored.capture());
        assertThat(restored.getValue().lines()).singleElement()
                .extracting(InventoryClient.ReserveLine::qty).isEqualTo(10);
    }

    @Test
    void keywordEscapesLikeWildcards() {
        assertThat(ExportSlipService.likePattern(" PXK_2026% ")).isEqualTo("%pxk!_2026!%%");
        assertThat(ExportSlipService.likePattern(" ")).isNull();
    }

    private ExportSlip confirmedSlip() {
        service.create(request(VatRate.NONE, line(1, 1, null, List.of("SN-1")), line(7, 2, "50", null)));
        when(slips.lock(9)).thenReturn(Optional.of(saved));
        service.confirm(9);
        return saved;
    }

    private void product(int id, String code, boolean hasSerial, Integer warranty, String price) {
        Map<String, Object> p = new HashMap<>(Map.of("id", id, "code", code, "name", "SP " + code, "unit", "cái",
                "hasSerial", hasSerial, "deleted", false, "defaultPriceOut", new BigDecimal(price)));
        if (warranty != null) {
            p.put("warrantyMonths", warranty);
        }
        products.put(id, p);
    }

    private static com.microinvent.sales.web.dto.SplitRequest split(int warehouseId,
            com.microinvent.sales.web.dto.SplitRequest.Line... lines) {
        return new com.microinvent.sales.web.dto.SplitRequest(
                List.of(new com.microinvent.sales.web.dto.SplitRequest.Allocation(warehouseId, List.of(lines))));
    }

    private static com.microinvent.sales.web.dto.SplitRequest.Line splitLine(int productId, int qty,
            List<String> serials) {
        return new com.microinvent.sales.web.dto.SplitRequest.Line(productId, qty, serials);
    }

    private static ExportSlipRequest request(VatRate vat, ExportSlipRequest.Line... lines) {
        return new ExportSlipRequest(1, null, 5, null, null, vat, null, null, null, null, List.of(lines));
    }

    private static ExportSlipRequest.Line line(int productId, int qty, String price, List<String> serials) {
        return new ExportSlipRequest.Line(productId, qty, price == null ? null : new BigDecimal(price), serials);
    }
}
