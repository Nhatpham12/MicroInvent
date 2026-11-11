package com.microinvent.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.inventory.domain.StockBalance;
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
import com.microinvent.inventory.web.dto.StockReceiveRequest;
import com.microinvent.inventory.web.dto.StockReceiveRequest.Condition;
import com.microinvent.inventory.web.dto.StockResult;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class InternalStockServiceTest {

    private WarehouseRepository warehouses;
    private StockBalanceRepository balances;
    private StockItemRepository items;
    private StockMovementRepository movements;
    private StockHoldRepository holds;
    private InternalStockService service;
    /** Dòng tồn giả lập theo productId tại kho 1. */
    private final Map<Integer, StockBalance> stock = new HashMap<>();

    @BeforeEach
    void setUp() {
        warehouses = mock(WarehouseRepository.class);
        balances = mock(StockBalanceRepository.class);
        items = mock(StockItemRepository.class);
        movements = mock(StockMovementRepository.class);
        holds = mock(StockHoldRepository.class);
        service = new InternalStockService(warehouses, balances, items, movements, holds);

        when(warehouses.findById(1)).thenReturn(Optional.of(warehouse(1, Warehouse.Status.ACTIVE, false)));
        when(balances.lock(any(), any())).thenAnswer(inv -> Optional.of(
                stock.computeIfAbsent(inv.getArgument(0), p -> balance(p, 1, 0, "0"))));
        when(movements.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(items.findExistingSerials(anyCollection())).thenReturn(List.of());
    }

    @Test
    void receiveAddsStockUpdatesAverageCostAndLogsMovement() {
        stock.put(5, balance(5, 1, 10, "100.00"));

        StockResult res = service.receive(receive(StockMovement.RefType.RECEIPT,
                line(5, 10, null, "200", Condition.GOOD)));

        StockBalance b = stock.get(5);
        assertThat(b.getQtyOnHand()).isEqualTo(20);
        assertThat(b.getAvgCost()).isEqualByComparingTo("150.00");
        StockResult.Movement m = res.movements().get(0);
        assertThat(m.movementType()).isEqualTo(StockMovement.Type.IMPORT);
        assertThat(List.of(m.qtyChange(), m.qtyBefore(), m.qtyAfter())).containsExactly(10, 10, 20);
        verify(balances).insertIfAbsent(5, 1);
    }

    @Test
    void receiveWithSerialsCreatesItemsAndDamagedGoesToDamagedQty() {
        service.receive(receive(StockMovement.RefType.SALES_RETURN,
                line(1, 2, List.of("SN-A", "SN-B"), "38000000", Condition.DAMAGED)));

        ArgumentCaptor<StockItem> saved = ArgumentCaptor.forClass(StockItem.class);
        verify(items, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(StockItem::getSerial).containsExactly("SN-A", "SN-B");
        assertThat(saved.getAllValues()).extracting(StockItem::getStatus).containsOnly(StockItem.Status.DAMAGED);
        assertThat(saved.getAllValues()).extracting(StockItem::getReceiptId).containsOnly((Integer) null);
        assertThat(stock.get(1).getQtyDamaged()).isEqualTo(2);
        assertThat(stock.get(1).available()).isZero();
    }

    @Test
    void receiveLocksLinesInProductOrder() {
        service.receive(receive(StockMovement.RefType.RECEIPT,
                line(7, 1, null, "1", null), line(2, 1, null, "1", null), line(5, 1, null, "1", null)));

        InOrder order = inOrder(balances);
        order.verify(balances).lock(2, 1);
        order.verify(balances).lock(5, 1);
        order.verify(balances).lock(7, 1);
    }

    @Test
    void receiveRejectsExistingSerial() {
        when(items.findExistingSerials(anyCollection())).thenReturn(List.of("SN-A"));

        assertThatThrownBy(() -> service.receive(receive(StockMovement.RefType.RECEIPT,
                line(1, 2, List.of("SN-A", "SN-B"), "1", null))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    assertThat(((BusinessException) ex).getCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(((BusinessException) ex).getDetails()).isEqualTo(Map.of("serials", List.of("SN-A")));
                });
        verify(balances, never()).lock(any(), any());
    }

    @Test
    void receiveValidatesSerialCountAndDuplicatesInRequest() {
        assertThatThrownBy(() -> service.receive(receive(StockMovement.RefType.RECEIPT,
                line(1, 3, List.of("SN-A", "SN-B"), "1", null))))
                .extracting("code").isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThatThrownBy(() -> service.receive(receive(StockMovement.RefType.RECEIPT,
                line(1, 1, List.of("SN-A"), "1", null), line(2, 1, List.of("SN-A"), "1", null))))
                .extracting("code").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void receiveRejectsWrongRefTypeAndInactiveOrLockedWarehouse() {
        assertThatThrownBy(() -> service.receive(receive(StockMovement.RefType.EXPORT_SLIP, line(1, 1, null, "1", null))))
                .extracting("code").isEqualTo(ErrorCode.VALIDATION_ERROR);

        when(warehouses.findById(1)).thenReturn(Optional.of(warehouse(1, Warehouse.Status.SUSPENDED, false)));
        assertThatThrownBy(() -> service.receive(receive(StockMovement.RefType.RECEIPT, line(1, 1, null, "1", null))))
                .extracting("code").isEqualTo(ErrorCode.CONFLICT);

        when(warehouses.findById(1)).thenReturn(Optional.of(warehouse(1, Warehouse.Status.ACTIVE, true)));
        assertThatThrownBy(() -> service.receive(receive(StockMovement.RefType.RECEIPT, line(1, 1, null, "1", null))))
                .extracting("code").isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void checkReportsShortageAndOtherWarehouses() {
        when(warehouses.findAll()).thenReturn(List.of(warehouse(1, Warehouse.Status.ACTIVE, false),
                warehouse(2, Warehouse.Status.ACTIVE, false)));
        StockBalance here = balance(5, 1, 10, "0");
        here.setQtyHold(4);
        when(balances.findByProductIdAndWarehouseId(5, 1)).thenReturn(Optional.of(here));
        when(balances.findAvailableElsewhere(5, 1)).thenReturn(List.of(balance(5, 2, 8, "0")));
        when(balances.findByProductIdAndWarehouseId(9, 1)).thenReturn(Optional.empty());

        StockCheckResponse res = service.check(new StockCheckRequest(1,
                List.of(new StockCheckRequest.Item(5, 10), new StockCheckRequest.Item(9, 1))));

        assertThat(res.enough()).isFalse();
        StockCheckResponse.Line p5 = res.lines().get(0);
        assertThat(List.of(p5.available(), p5.shortage())).containsExactly(6, 4);
        assertThat(p5.otherWarehouses()).containsExactly(new StockCheckResponse.OtherWarehouse(2, "Kho 2", 8));
        assertThat(res.lines().get(1).shortage()).isEqualTo(1);
    }

    @Test
    void weightedAverageHandlesEmptyStock() {
        assertThat(InternalStockService.weightedAverage(BigDecimal.ZERO, 0, new BigDecimal("99.99"), 3))
                .isEqualByComparingTo("99.99");
        assertThat(InternalStockService.weightedAverage(new BigDecimal("10"), 1, new BigDecimal("20"), 2))
                .isEqualByComparingTo("16.67");
    }

    private static StockReceiveRequest receive(StockMovement.RefType refType, StockReceiveRequest.Line... lines) {
        return new StockReceiveRequest(1, refType, 100, 4, List.of(lines));
    }

    private static StockReceiveRequest.Line line(int productId, int qty, List<String> serials, String unitCost,
            Condition condition) {
        return new StockReceiveRequest.Line(productId, qty, serials, new BigDecimal(unitCost), null, condition);
    }

    private static StockBalance balance(int productId, int warehouseId, int onHand, String avgCost) {
        StockBalance b = new StockBalance();
        b.setProductId(productId);
        b.setWarehouseId(warehouseId);
        b.setQtyOnHand(onHand);
        b.setAvgCost(new BigDecimal(avgCost));
        return b;
    }

    private static Warehouse warehouse(int id, Warehouse.Status status, boolean locked) {
        Warehouse w = new Warehouse();
        w.setId(id);
        w.setCode("W" + id);
        w.setName("Kho " + id);
        w.setStatus(status);
        w.setStocktakeLocked(locked);
        return w;
    }
}
