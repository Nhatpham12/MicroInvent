package com.microinvent.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microinvent.common.context.UserContext;
import com.microinvent.common.context.UserContextHolder;
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
import com.microinvent.inventory.web.dto.DamagedRequest;
import com.microinvent.inventory.web.dto.HoldExtendRequest;
import com.microinvent.inventory.web.dto.HoldRequest;
import com.microinvent.inventory.web.dto.HoldView;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HoldServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T03:00:00Z"), ZONE);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final UserContext SALES = new UserContext(6L, "banhang", Set.of("SALES"), Set.of(1L), false, 1L);

    private StockBalanceRepository balances;
    private StockItemRepository items;
    private StockHoldRepository holds;
    private StockMovementRepository movements;
    private HoldService service;
    private StockBalance balance;

    @BeforeEach
    void setUp() {
        WarehouseRepository warehouses = mock(WarehouseRepository.class);
        balances = mock(StockBalanceRepository.class);
        items = mock(StockItemRepository.class);
        holds = mock(StockHoldRepository.class);
        movements = mock(StockMovementRepository.class);
        service = new HoldService(warehouses, balances, items, holds, movements, CLOCK, 3);
        UserContextHolder.set(SALES);

        Warehouse w = new Warehouse();
        w.setId(1);
        w.setCode("HN01");
        w.setName("Kho Hà Nội");
        w.setStatus(Warehouse.Status.ACTIVE);
        when(warehouses.findById(1)).thenReturn(Optional.of(w));
        balance = new StockBalance();
        balance.setProductId(5);
        balance.setWarehouseId(1);
        balance.setQtyOnHand(10);
        balance.setAvgCost(BigDecimal.TEN);
        when(balances.lock(5, 1)).thenReturn(Optional.of(balance));
        when(holds.save(any(StockHold.class))).thenAnswer(inv -> {
            StockHold h = inv.getArgument(0);
            h.setId(77L);
            return h;
        });
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void createHoldsQuantityWithDefaultExpiryAndHolder() {
        List<HoldView> r = service.create(new HoldRequest(1, 5, 4, null, 9, " Công ty A ", null));

        assertThat(balance.getQtyHold()).isEqualTo(4);
        assertThat(r).singleElement().satisfies(h -> {
            assertThat(h.refType()).isEqualTo(StockHold.RefType.MANUAL);
            assertThat(h.expiresAt()).isEqualTo(NOW.plusDays(3));
            assertThat(h.heldByUserId()).isEqualTo(6);
            assertThat(h.customerName()).isEqualTo("Công ty A");
            assertThat(h.warehouseName()).isEqualTo("Kho Hà Nội");
        });
    }

    @Test
    void createRejectsShortStockPastExpiryOtherWarehouseAndSerialRules() {
        assertThatThrownBy(() -> service.create(new HoldRequest(1, 5, 11, null, null, null, null)))
                .extracting("code").isEqualTo(ErrorCode.STOCK_NOT_ENOUGH);
        assertThatThrownBy(() -> service.create(new HoldRequest(1, 5, 1, null, null, null, NOW.minusMinutes(1))))
                .extracting("code").isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThatThrownBy(() -> service.create(new HoldRequest(2, 5, 1, null, null, null, null)))
                .extracting("code").isEqualTo(ErrorCode.WAREHOUSE_FORBIDDEN);
        assertThatThrownBy(() -> service.create(new HoldRequest(1, 5, 1, List.of("SN-1"), null, null, null)))
                .extracting("code").isEqualTo(ErrorCode.VALIDATION_ERROR); // SP không quản lý serial
        when(items.existsByProductId(5)).thenReturn(true);
        assertThatThrownBy(() -> service.create(new HoldRequest(1, 5, 2, List.of("SN-1"), null, null, null)))
                .extracting("code").isEqualTo(ErrorCode.VALIDATION_ERROR); // thiếu serial
        assertThat(balance.getQtyHold()).isZero();
        verify(holds, never()).save(any());
    }

    @Test
    void createWithSerialsHoldsEachUnit() {
        when(items.existsByProductId(5)).thenReturn(true);
        StockItem a = item(11L, "SN-A", StockItem.Status.AVAILABLE);
        StockItem b = item(12L, "SN-B", StockItem.Status.SOLD);
        when(items.lockBySerials(anyCollection())).thenReturn(List.of(a));

        List<HoldView> r = service.create(new HoldRequest(1, 5, 1, List.of("SN-A"), null, null, null));
        assertThat(r).singleElement().satisfies(h -> assertThat(List.of(h.serial(), h.qty())).containsExactly("SN-A", 1));
        assertThat(a.getStatus()).isEqualTo(StockItem.Status.HOLD);

        when(items.lockBySerials(anyCollection())).thenReturn(List.of(b));
        assertThatThrownBy(() -> service.create(new HoldRequest(1, 5, 1, List.of("SN-B"), null, null, null)))
                .extracting("code").isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void extendOnlyManualActiveAndLater() {
        StockHold h = manualHold(NOW.plusDays(1));
        when(holds.lockById(77L)).thenReturn(Optional.of(h));

        assertThatThrownBy(() -> service.extend(77L, new HoldExtendRequest(NOW.plusHours(1))))
                .extracting("code").isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(service.extend(77L, new HoldExtendRequest(NOW.plusDays(5))).expiresAt()).isEqualTo(NOW.plusDays(5));

        h.setRefType(StockHold.RefType.EXPORT_SLIP);
        h.setRefId(9);
        assertThatThrownBy(() -> service.extend(77L, new HoldExtendRequest(NOW.plusDays(9))))
                .extracting("code").isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void releaseReturnsQuantityAndRejectsDocumentHoldsOrTwice() {
        balance.setQtyHold(4);
        StockHold h = manualHold(NOW.plusDays(1));
        h.setQty(4);
        when(holds.findById(77L)).thenReturn(Optional.of(h));
        when(holds.lockById(77L)).thenReturn(Optional.of(h));

        assertThat(service.release(77L).status()).isEqualTo(StockHold.Status.RELEASED);
        assertThat(balance.getQtyHold()).isZero();
        assertThatThrownBy(() -> service.release(77L)).extracting("code").isEqualTo(ErrorCode.CONFLICT);

        StockHold doc = manualHold(null);
        doc.setId(78L);
        doc.setRefType(StockHold.RefType.EXPORT_SLIP);
        when(holds.findById(78L)).thenReturn(Optional.of(doc));
        assertThatThrownBy(() -> service.release(78L)).extracting("code").isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void expireOnlyWhenStillActiveAndPastDue() {
        balance.setQtyHold(2);
        StockHold due = manualHold(NOW.minusMinutes(1));
        due.setQty(2);
        when(holds.findById(77L)).thenReturn(Optional.of(due));
        when(holds.lockById(77L)).thenReturn(Optional.of(due));

        assertThat(service.expire(77L)).isTrue();
        assertThat(due.getStatus()).isEqualTo(StockHold.Status.EXPIRED);
        assertThat(balance.getQtyHold()).isZero();
        assertThat(service.expire(77L)).isFalse(); // đã nhả

        StockHold extended = manualHold(NOW.plusDays(1)); // được gia hạn trong lúc chờ khóa
        extended.setId(79L);
        when(holds.findById(79L)).thenReturn(Optional.of(extended));
        when(holds.lockById(79L)).thenReturn(Optional.of(extended));
        assertThat(service.expire(79L)).isFalse();
        assertThat(extended.getStatus()).isEqualTo(StockHold.Status.ACTIVE);
    }

    @Test
    void markDamagedMovesAvailableToDamagedAndLogsMovement() {
        UserContextHolder.set(new UserContext(4L, "thukho", Set.of("STOREKEEPER"), Set.of(1L), false, 1L));
        balance.setQtyHold(7);

        HoldService.DamagedResult r = service.markDamaged(new DamagedRequest(1, 5, 2, null));

        assertThat(List.of(r.onHand(), r.damaged(), r.available())).containsExactly(10, 2, 1);
        ArgumentCaptor<StockMovement> m = ArgumentCaptor.forClass(StockMovement.class);
        verify(movements).save(m.capture());
        assertThat(m.getValue().getMovementType()).isEqualTo(StockMovement.Type.DAMAGED);
        assertThat(m.getValue().getRefId()).isZero(); // cột ref_id NOT NULL
        assertThat(m.getValue().getUserId()).isEqualTo(4);
        assertThat(List.of(m.getValue().getQtyChange(), m.getValue().getQtyBefore(), m.getValue().getQtyAfter()))
                .containsExactly(2, 0, 2);
        assertThatThrownBy(() -> service.markDamaged(new DamagedRequest(1, 5, 2, null)))
                .extracting("code").isEqualTo(ErrorCode.STOCK_NOT_ENOUGH);
    }

    private static StockHold manualHold(LocalDateTime expiresAt) {
        StockHold h = new StockHold();
        h.setId(77L);
        h.setProductId(5);
        h.setWarehouseId(1);
        h.setQty(1);
        h.setHeldByUserId(6);
        h.setRefType(StockHold.RefType.MANUAL);
        h.setExpiresAt(expiresAt);
        return h;
    }

    private static StockItem item(Long id, String serial, StockItem.Status status) {
        StockItem i = new StockItem();
        i.setId(id);
        i.setProductId(5);
        i.setWarehouseId(1);
        i.setSerial(serial);
        i.setStatus(status);
        return i;
    }
}
