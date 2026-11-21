package com.microinvent.inventory.web;

import com.microinvent.common.web.ApiResponse;
import com.microinvent.inventory.domain.Warehouse;
import com.microinvent.inventory.repository.WarehouseRepository;
import com.microinvent.inventory.web.dto.WarehouseExistsResponse;
import com.microinvent.inventory.web.dto.WarehouseIdsRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API nội bộ về kho cho service khác (X-Internal-Key kiểm bởi InternalApiKeyFilter).
 * identity-service gọi trước khi gán kho / đổi kho mặc định cho người dùng.
 */
@Tag(name = "Internal – kho")
@RestController
@RequestMapping("/internal/warehouses")
public class InternalWarehouseController {

    private final WarehouseRepository warehouses;

    public InternalWarehouseController(WarehouseRepository warehouses) {
        this.warehouses = warehouses;
    }

    @Operation(summary = "Kiểm danh sách id kho: id nào không tồn tại, id nào đã ngừng hoạt động")
    @PostMapping("/exists")
    @Transactional(readOnly = true)
    public ApiResponse<WarehouseExistsResponse> exists(@Valid @RequestBody WarehouseIdsRequest req) {
        Set<Integer> ids = new LinkedHashSet<>(req.ids());
        Map<Integer, Warehouse> found = warehouses.findAllById(ids).stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity()));
        List<Integer> missing = ids.stream().filter(id -> !found.containsKey(id)).toList();
        List<Integer> closed = ids.stream()
                .filter(id -> found.containsKey(id) && found.get(id).getStatus() == Warehouse.Status.CLOSED)
                .toList();
        return ApiResponse.ok(new WarehouseExistsResponse(missing, closed));
    }
}
