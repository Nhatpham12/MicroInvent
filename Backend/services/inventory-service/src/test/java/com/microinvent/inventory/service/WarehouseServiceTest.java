package com.microinvent.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microinvent.common.context.UserContext;
import com.microinvent.common.context.UserContextHolder;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.inventory.domain.ProductWarehouseSetting;
import com.microinvent.inventory.domain.Warehouse;
import com.microinvent.inventory.repository.ProductWarehouseSettingRepository;
import com.microinvent.inventory.repository.WarehouseRepository;
import com.microinvent.inventory.web.dto.CloseCheckResponse;
import com.microinvent.inventory.web.dto.CloseCheckResponse.BlockerType;
import com.microinvent.inventory.web.dto.MinStockRequest;
import com.microinvent.inventory.web.dto.WarehouseRequest;
import com.microinvent.inventory.web.dto.WarehouseResponse;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class WarehouseServiceTest {

    private static final UserContext ADMIN = new UserContext(1L, "admin", Set.of("ADMIN"), Set.of(), true, null);
    /** Thủ kho chỉ được gán kho 1. */
    private static final UserContext STOREKEEPER =
            new UserContext(4L, "thukho.hn", Set.of("STOREKEEPER"), Set.of(1L), false, 1L);

    private WarehouseRepository repository;
    private ProductWarehouseSettingRepository settings;
    private WarehouseService service;

    @BeforeEach
    void setUp() {
        repository = mock(WarehouseRepository.class);
        settings = mock(ProductWarehouseSettingRepository.class);
        service = new WarehouseService(repository, settings);
        when(repository.save(any(Warehouse.class))).thenAnswer(inv -> inv.getArgument(0));
        UserContextHolder.set(ADMIN);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void listForStorekeeperIsLimitedToAssignedWarehouses() {
        UserContextHolder.set(STOREKEEPER);
        Page<Warehouse> page = new PageImpl<>(List.of(warehouse(1, Warehouse.Status.ACTIVE, false)));
        when(repository.search(any(), any(), any(), eq(false), eq(Set.of(1)), any())).thenReturn(page);

        var result = service.list(null, null, null, PageRequest.of(0, 20));

        assertThat(result.content()).extracting(WarehouseResponse::id).containsExactly(1);
        verify(repository).search(null, null, null, false, Set.of(1), PageRequest.of(0, 20));
    }

    @Test
    void storekeeperCannotViewOtherWarehouse() {
        UserContextHolder.set(STOREKEEPER);

        assertThatThrownBy(() -> service.detail(2))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.WAREHOUSE_FORBIDDEN);
        verify(repository, never()).findById(anyInt());
    }

    @Test
    void createUppercasesCodeAndRejectsDuplicate() {
        when(repository.existsByCodeIgnoreCase("HN02")).thenReturn(false);
        WarehouseResponse created = service.create(request(" hn02 "));
        assertThat(created.code()).isEqualTo("HN02");
        assertThat(created.status()).isEqualTo(Warehouse.Status.ACTIVE);

        when(repository.existsByCodeIgnoreCase("HN01")).thenReturn(true);
        assertThatThrownBy(() -> service.create(request("HN01")))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void closeIsBlockedWhileStockRemains() {
        when(repository.findById(3)).thenReturn(Optional.of(warehouse(3, Warehouse.Status.ACTIVE, false)));
        when(repository.sumOnHand(3)).thenReturn(5L);

        assertThatThrownBy(() -> service.changeStatus(3, Warehouse.Status.CLOSED))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ErrorCode.WAREHOUSE_CANNOT_CLOSE);
                    assertThat(be.getDetails()).asList()
                            .extracting("type").containsExactly(BlockerType.STOCK_REMAINING);
                });
    }

    @Test
    void closeCheckListsEveryBlocker() {
        when(repository.findById(3)).thenReturn(Optional.of(warehouse(3, Warehouse.Status.ACTIVE, false)));
        when(repository.sumOnHand(3)).thenReturn(5L);
        when(repository.sumInTransitIn(3)).thenReturn(2L);
        when(repository.countTransfersInTransit(3)).thenReturn(1L);
        when(repository.countOpenDocuments(3)).thenReturn(1L);
        when(repository.countActiveHolds(3)).thenReturn(4L);
        when(repository.sumRentedLoaned(3)).thenReturn(1L);

        CloseCheckResponse check = service.closeCheck(3);

        assertThat(check.canClose()).isFalse();
        assertThat(check.blockers()).extracting("type").containsExactly(BlockerType.STOCK_REMAINING,
                BlockerType.IN_TRANSIT, BlockerType.OPEN_DOCUMENTS, BlockerType.ACTIVE_HOLDS,
                BlockerType.ACTIVE_RENTALS);
        assertThat(check.blockers().get(1).count()).isEqualTo(3);
    }

    @Test
    void emptyWarehouseCanBeClosed() {
        Warehouse w = warehouse(3, Warehouse.Status.ACTIVE, false);
        when(repository.findById(3)).thenReturn(Optional.of(w));

        WarehouseResponse res = service.changeStatus(3, Warehouse.Status.CLOSED);

        assertThat(res.status()).isEqualTo(Warehouse.Status.CLOSED);
        assertThat(w.getStatus()).isEqualTo(Warehouse.Status.CLOSED);
    }

    @Test
    void defaultWarehouseCannotBeSuspended() {
        when(repository.findById(1)).thenReturn(Optional.of(warehouse(1, Warehouse.Status.ACTIVE, true)));

        assertThatThrownBy(() -> service.changeStatus(1, Warehouse.Status.SUSPENDED))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void setDefaultClearsOtherDefaultsAndRequiresActive() {
        Warehouse active = warehouse(2, Warehouse.Status.ACTIVE, false);
        when(repository.findById(2)).thenReturn(Optional.of(active));

        assertThat(service.setDefault(2).isDefault()).isTrue();
        verify(repository).clearDefaultExcept(2);

        when(repository.findById(3)).thenReturn(Optional.of(warehouse(3, Warehouse.Status.SUSPENDED, false)));
        assertThatThrownBy(() -> service.setDefault(3))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.CONFLICT);
        verify(repository, never()).clearDefaultExcept(3);
    }

    @Test
    void updateMinStockSavesValuesAndDeletesNulls() {
        when(repository.findById(1)).thenReturn(Optional.of(warehouse(1, Warehouse.Status.ACTIVE, true)));
        MinStockRequest req = new MinStockRequest(List.of(new MinStockRequest.Item(5, 20),
                new MinStockRequest.Item(7, null)));

        service.updateMinStock(1, req);

        verify(settings).save(any(ProductWarehouseSetting.class));
        verify(settings).deleteById(new ProductWarehouseSetting.Key(7, 1));
    }

    @Test
    void storekeeperCannotReadMinStockOfOtherWarehouse() {
        UserContextHolder.set(STOREKEEPER);

        assertThatThrownBy(() -> service.listMinStock(2))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.WAREHOUSE_FORBIDDEN);
        verify(repository, never()).sumStock(anyCollection());
    }

    private static Warehouse warehouse(int id, Warehouse.Status status, boolean isDefault) {
        Warehouse w = new Warehouse();
        w.setId(id);
        w.setCode("W" + id);
        w.setName("Kho " + id);
        w.setType(Warehouse.Type.BRANCH);
        w.setAddress("Địa chỉ " + id);
        w.setStatus(status);
        w.setDefaultWarehouse(isDefault);
        return w;
    }

    private static WarehouseRequest request(String code) {
        return new WarehouseRequest(code, "Kho mới", Warehouse.Type.BRANCH, "Số 1", null, null, null, null,
                "#1677FF", null);
    }
}
