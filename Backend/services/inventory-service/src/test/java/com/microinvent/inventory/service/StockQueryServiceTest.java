package com.microinvent.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microinvent.common.context.UserContext;
import com.microinvent.common.context.UserContextHolder;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.inventory.domain.StockBalance;
import com.microinvent.inventory.domain.StockItem;
import com.microinvent.inventory.domain.StockMovement;
import com.microinvent.inventory.domain.Warehouse;
import com.microinvent.inventory.repository.StockBalanceRepository;
import com.microinvent.inventory.repository.StockItemRepository;
import com.microinvent.inventory.repository.StockMovementRepository;
import com.microinvent.inventory.repository.WarehouseRepository;
import com.microinvent.inventory.web.dto.ProductStockView;
import com.microinvent.inventory.web.dto.StockItemView;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class StockQueryServiceTest {

    private static final UserContext ADMIN = new UserContext(1L, "admin", Set.of("ADMIN"), Set.of(), true, null);
    private static final UserContext STOREKEEPER =
            new UserContext(4L, "thukho", Set.of("STOREKEEPER"), Set.of(1L), false, 1L);

    private StockBalanceRepository balances;
    private StockItemRepository items;
    private StockMovementRepository movements;
    private StockQueryService service;

    @BeforeEach
    void setUp() {
        WarehouseRepository warehouses = mock(WarehouseRepository.class);
        balances = mock(StockBalanceRepository.class);
        items = mock(StockItemRepository.class);
        movements = mock(StockMovementRepository.class);
        service = new StockQueryService(warehouses, balances, items, movements);
        Warehouse w = new Warehouse();
        w.setId(1);
        w.setName("Kho HN01");
        when(warehouses.findAll()).thenReturn(List.of(w));
        UserContextHolder.set(ADMIN);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void storekeeperSeesOnlyAssignedWarehousesAndCannotAskForOthers() {
        UserContextHolder.set(STOREKEEPER);
        when(balances.search(anyBoolean(), any(), any(), any(), any())).thenReturn(Page.empty());

        service.list(null, null, null, PageRequest.of(0, 20));
        verify(balances).search(eq(false), eq(Set.of(1)), any(), any(), any());

        assertThatThrownBy(() -> service.list(2, null, null, PageRequest.of(0, 20)))
                .extracting("code").isEqualTo(ErrorCode.WAREHOUSE_FORBIDDEN);
    }

    @Test
    void adminWithoutWarehouseSeesAllAndUnknownSortIsRejected() {
        when(balances.search(anyBoolean(), any(), any(), any(), any())).thenReturn(Page.empty());
        service.list(null, null, true, PageRequest.of(0, 20));
        verify(balances).search(eq(true), any(), any(), eq(true), any());

        assertThatThrownBy(() -> service.list(null, null, null, PageRequest.of(0, 20, Sort.by("available"))))
                .extracting("code").isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThatThrownBy(() -> service.listMovements(null, null, null, null, null,
                PageRequest.of(0, 20, Sort.by("qtyChange")))).extracting("code").isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(movements, never()).search(anyBoolean(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void byProductSumsAllWarehouses() {
        StockBalance a = balance(1, 10, 2, 1);
        StockBalance b = balance(2, 5, 0, 0);
        b.setQtyInTransitIn(3);
        when(balances.findByProductInScope(eq(9), eq(true), any())).thenReturn(List.of(a, b));

        ProductStockView v = service.byProduct(9);

        assertThat(List.of(v.totals().onHand(), v.totals().hold(), v.totals().damaged(), v.totals().available(),
                v.totals().inTransitIn())).containsExactly(15, 2, 1, 12, 3);
        assertThat(v.warehouses().get(0).warehouseName()).isEqualTo("Kho HN01");
        assertThat(v.warehouses().get(0).stockValue()).isEqualByComparingTo("1000");
    }

    @Test
    void serialLookupReturnsReceiptAndCurrentDocumentHistory() {
        StockItem i = new StockItem();
        i.setProductId(1);
        i.setSerial("SN-001");
        i.setStatus(StockItem.Status.SOLD);
        i.setReceiptId(11);
        i.setCurrentRefType("EXPORT_SLIP");
        i.setCurrentRefId(211);
        when(items.findBySerial("SN-001")).thenReturn(Optional.of(i));
        when(movements.findByProductIdAndRefTypeAndRefIdOrderById(1, StockMovement.RefType.RECEIPT, 11))
                .thenReturn(List.of(movement(StockMovement.Type.IMPORT, 3)));
        when(movements.findByProductIdAndRefTypeAndRefIdOrderById(1, StockMovement.RefType.EXPORT_SLIP, 211))
                .thenReturn(List.of(movement(StockMovement.Type.EXPORT, -1)));

        StockItemView v = service.bySerial(" SN-001 ");

        assertThat(v.status()).isEqualTo(StockItem.Status.SOLD);
        assertThat(v.history()).extracting(m -> m.movementType())
                .containsExactly(StockMovement.Type.IMPORT, StockMovement.Type.EXPORT);
        assertThatThrownBy(() -> service.bySerial("NOPE")).extracting("code").isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void lowStockComputesShortage() {
        StockBalanceRepository.BelowMinRow row = mock(StockBalanceRepository.BelowMinRow.class);
        when(row.getProductId()).thenReturn(5);
        when(row.getWarehouseId()).thenReturn(1);
        when(row.getMinStock()).thenReturn(20);
        when(row.getAvailable()).thenReturn(7);
        when(balances.findBelowMin(eq(true), any())).thenReturn(List.of(row));

        assertThat(service.lowStock(null)).singleElement()
                .satisfies(l -> assertThat(List.of(l.available(), l.minStock(), l.shortage())).containsExactly(7, 20, 13));
    }

    private static StockBalance balance(int warehouseId, int onHand, int hold, int damaged) {
        StockBalance b = new StockBalance();
        b.setProductId(9);
        b.setWarehouseId(warehouseId);
        b.setQtyOnHand(onHand);
        b.setQtyHold(hold);
        b.setQtyDamaged(damaged);
        b.setAvgCost(new BigDecimal("100"));
        return b;
    }

    private static StockMovement movement(StockMovement.Type type, int change) {
        StockMovement m = new StockMovement();
        m.setMovementType(type);
        m.setQtyChange(change);
        return m;
    }
}
