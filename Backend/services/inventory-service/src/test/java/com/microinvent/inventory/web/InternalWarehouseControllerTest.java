package com.microinvent.inventory.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.microinvent.inventory.domain.Warehouse;
import com.microinvent.inventory.repository.WarehouseRepository;
import com.microinvent.inventory.web.dto.WarehouseExistsResponse;
import com.microinvent.inventory.web.dto.WarehouseIdsRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class InternalWarehouseControllerTest {

    @Test
    void reportsMissingAndClosedWarehouses() {
        WarehouseRepository repo = mock(WarehouseRepository.class);
        when(repo.findAllById(any())).thenReturn(List.of(warehouse(1, Warehouse.Status.ACTIVE),
                warehouse(2, Warehouse.Status.CLOSED), warehouse(3, Warehouse.Status.SUSPENDED)));

        WarehouseExistsResponse res = new InternalWarehouseController(repo)
                .exists(new WarehouseIdsRequest(List.of(1, 2, 3, 99, 99))).data();

        assertThat(res.missing()).containsExactly(99);
        assertThat(res.closed()).containsExactly(2);
    }

    private static Warehouse warehouse(int id, Warehouse.Status status) {
        Warehouse w = new Warehouse();
        w.setId(id);
        w.setStatus(status);
        return w;
    }
}
