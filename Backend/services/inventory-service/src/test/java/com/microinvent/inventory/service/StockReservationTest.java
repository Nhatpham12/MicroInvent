package com.microinvent.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.microinvent.inventory.web.dto.StockCommitRequest;
import com.microinvent.inventory.web.dto.StockReleaseRequest;
import com.microinvent.inventory.web.dto.StockReserveRequest;
import com.microinvent.inventory.web.dto.StockResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** reserve / commit / release của InternalStockService. */
class StockReservationTest {

    private StockBalanceRepository balances;
    private StockItemRepository items;
    private StockHoldRepository holds;
    private InternalStockService service;
    /** Tồn giả lập tại kho 1 theo productId. */
    private final Map<Integer, StockBalance> stock = new HashMap<>();
    /** Hold đã lưu (giả lập bảng stock_holds). */
    private final List<StockHold> savedHolds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        WarehouseRepository warehouses = mock(WarehouseRepository.class);
        balances = mock(StockBalanceRepository.class);
        items = mock(StockItemRepository.class);
        StockMovementRepository movements = mock(StockMovementRepository.class);
        holds = mock(StockHoldRepository.class);
        service = new InternalStockService(warehouses, balances, items, movements, holds);

        Warehouse w = new Warehouse();
        w.setId(1);
        w.setCode("HN01");
        w.setStatus(Warehouse.Status.ACTIVE);
        when(warehouses.findById(1)).thenReturn(Optional.of(w));
        when(balances.lock(any(), any())).thenAnswer(inv -> Optional.ofNullable(stock.get(inv.<Integer>getArgument(0))));
        when(movements.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(holds.save(any(StockHold.class))).thenAnswer(inv -> {
            StockHold h = inv.getArgument(0);
            h.setId((long) savedHolds.size() + 1);
            savedHolds.add(h);
            return h;
        });
        when(holds.findByRefTypeAndRefIdAndStatus(any(), anyInt(), any())).thenAnswer(inv -> activeHolds());
        when(holds.lockActiveByRef(any(), anyInt())).thenAnswer(inv -> activeHolds());
    }

    @Test
    void reserveFailsWholeRequestWhenOneLineIsShort() {
        stock.put(5, balance(5, 10, 0));
        stock.put(7, balance(7, 2, 0));

        assertThatThrownBy(() -> service.reserve(reserve(line(5, 3, null), line(7, 5, null))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    assertThat(((BusinessException) ex).getCode()).isEqualTo(ErrorCode.STOCK_NOT_ENOUGH);
                    assertThat(((BusinessException) ex).getDetails())
                            .isEqualTo(List.of(Map.of("productId", 7, "requested", 5, "available", 2)));
                });
        assertThat(stock.get(5).getQtyHold()).isZero();
        verify(holds, never()).save(any());
    }

    @Test
    void reserveHoldsSerialsAndPlainQuantity() {
        stock.put(1, balance(1, 3, 0));
        StockItem a = item(11L, 1, "SN-A", StockItem.Status.AVAILABLE, "100");
        when(items.lockBySerials(anyCollection())).thenReturn(List.of(a));

        StockResult res = service.reserve(reserve(line(1, 1, List.of("SN-A")), line(1, 2, null)));

        assertThat(stock.get(1).getQtyHold()).isEqualTo(3);
        assertThat(a.getStatus()).isEqualTo(StockItem.Status.HOLD);
        assertThat(a.getCurrentRefId()).isEqualTo(50);
        assertThat(savedHolds).extracting(StockHold::getQty, StockHold::getStockItemId)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1, 11L), org.assertj.core.groups.Tuple.tuple(2, null));
        assertThat(res.holdIds()).containsExactly(1L, 2L);
    }

    @Test
    void reserveRejectsSerialThatIsNotAvailableHere() {
        stock.put(1, balance(1, 3, 0));
        when(items.lockBySerials(anyCollection()))
                .thenReturn(List.of(item(11L, 1, "SN-A", StockItem.Status.SOLD, "100")));

        assertThatThrownBy(() -> service.reserve(reserve(line(1, 1, List.of("SN-A")))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getDetails())
                        .isEqualTo(Map.of("serials", List.of("SN-A"))));
    }

    @Test
    void reserveRejectsSecondReserveForSameDocument() {
        stock.put(5, balance(5, 10, 0));
        service.reserve(reserve(line(5, 1, null)));

        assertThatThrownBy(() -> service.reserve(reserve(line(5, 1, null))))
                .extracting("code").isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void commitExportDeductsStockMarksSerialsSoldAndUsesSerialCost() {
        stock.put(1, balance(1, 3, 0));
        StockItem a = item(11L, 1, "SN-A", StockItem.Status.AVAILABLE, "100");
        StockItem b = item(12L, 1, "SN-B", StockItem.Status.AVAILABLE, "200");
        when(items.lockBySerials(anyCollection())).thenReturn(List.of(a, b));
        service.reserve(reserve(line(1, 2, List.of("SN-A", "SN-B"))));
        when(items.lockByIds(anyCollection())).thenReturn(List.of(a, b));

        StockResult res = service.commit(new StockCommitRequest(StockHold.RefType.EXPORT_SLIP, 50, 4,
                StockMovement.Type.EXPORT));

        StockBalance bal = stock.get(1);
        assertThat(List.of(bal.getQtyOnHand(), bal.getQtyHold(), bal.getQtyRentedLoaned())).containsExactly(1, 0, 0);
        assertThat(List.of(a, b)).extracting(StockItem::getStatus).containsOnly(StockItem.Status.SOLD);
        assertThat(a.getWarehouseId()).isNull();
        assertThat(a.getLastWarehouseId()).isEqualTo(1);
        assertThat(savedHolds).extracting(StockHold::getStatus).containsOnly(StockHold.Status.CONSUMED);
        assertThat(res.movements().get(0).qtyChange()).isEqualTo(-2);
        assertThat(res.unitCosts().get(0).unitCost()).isEqualByComparingTo("150.00");
    }

    @Test
    void commitRentMovesQuantityToRentedLoaned() {
        stock.put(7, balance(7, 10, 0));
        service.reserve(new StockReserveRequest(1, StockHold.RefType.RENTAL, 60, 4, null, null, List.of(line(7, 4, null))));

        service.commit(new StockCommitRequest(StockHold.RefType.RENTAL, 60, 4, StockMovement.Type.RENT));

        assertThat(List.of(stock.get(7).getQtyOnHand(), stock.get(7).getQtyRentedLoaned())).containsExactly(6, 4);
    }

    @Test
    void commitValidatesTypeAndNeedsActiveHolds() {
        assertThatThrownBy(() -> service.commit(new StockCommitRequest(StockHold.RefType.EXPORT_SLIP, 50, 4,
                StockMovement.Type.RENT))).extracting("code").isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThatThrownBy(() -> service.commit(new StockCommitRequest(StockHold.RefType.EXPORT_SLIP, 50, 4,
                StockMovement.Type.EXPORT))).extracting("code").isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void releaseReturnsHeldQuantityAndSerials() {
        stock.put(1, balance(1, 3, 0));
        StockItem a = item(11L, 1, "SN-A", StockItem.Status.AVAILABLE, "100");
        when(items.lockBySerials(anyCollection())).thenReturn(List.of(a));
        service.reserve(reserve(line(1, 1, List.of("SN-A")), line(1, 1, null)));
        when(items.lockByIds(anyCollection())).thenReturn(List.of(a));

        service.release(new StockReleaseRequest(StockHold.RefType.EXPORT_SLIP, 50));

        assertThat(stock.get(1).getQtyHold()).isZero();
        assertThat(a.getStatus()).isEqualTo(StockItem.Status.AVAILABLE);
        assertThat(a.getCurrentRefId()).isNull();
        assertThat(savedHolds).extracting(StockHold::getStatus).containsOnly(StockHold.Status.RELEASED);
        assertThat(service.release(new StockReleaseRequest(StockHold.RefType.EXPORT_SLIP, 50)).holdIds()).isEmpty();
    }

    private List<StockHold> activeHolds() {
        return savedHolds.stream().filter(h -> h.getStatus() == StockHold.Status.ACTIVE).toList();
    }

    private static StockReserveRequest reserve(StockReserveRequest.Line... lines) {
        return new StockReserveRequest(1, StockHold.RefType.EXPORT_SLIP, 50, 4, null, null, List.of(lines));
    }

    private static StockReserveRequest.Line line(int productId, int qty, List<String> serials) {
        return new StockReserveRequest.Line(productId, qty, serials);
    }

    private static StockBalance balance(int productId, int onHand, int hold) {
        StockBalance b = new StockBalance();
        b.setProductId(productId);
        b.setWarehouseId(1);
        b.setQtyOnHand(onHand);
        b.setQtyHold(hold);
        b.setAvgCost(new BigDecimal("120.00"));
        return b;
    }

    private static StockItem item(Long id, int productId, String serial, StockItem.Status status, String cost) {
        StockItem i = new StockItem();
        i.setId(id);
        i.setProductId(productId);
        i.setWarehouseId(1);
        i.setSerial(serial);
        i.setStatus(status);
        i.setCostPrice(new BigDecimal(cost));
        return i;
    }
}
