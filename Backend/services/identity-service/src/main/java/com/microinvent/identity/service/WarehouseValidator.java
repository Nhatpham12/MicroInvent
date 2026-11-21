package com.microinvent.identity.service;

import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.identity.client.InventoryClient;
import feign.FeignException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Kiểm kho có thật trước khi gán kho / đổi kho mặc định cho người dùng (BUG-06). Danh mục kho nằm ở
 * inventory-service nên gọi API nội bộ {@code POST /internal/warehouses/exists}.
 * <ul>
 * <li>Kho không tồn tại hoặc đã ngừng hoạt động (CLOSED) → 400, {@code details} liệt kê id sai.</li>
 * <li>inventory-service không gọi được → 503 (không gán bừa kho chưa kiểm).</li>
 * </ul>
 */
@Component
public class WarehouseValidator {

    private static final Logger log = LoggerFactory.getLogger(WarehouseValidator.class);

    private final InventoryClient inventory;

    public WarehouseValidator(InventoryClient inventory) {
        this.inventory = inventory;
    }

    public void requireExisting(Collection<Integer> warehouseIds) {
        List<Integer> ids = warehouseIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        InventoryClient.WarehouseExists res;
        try {
            res = inventory.exists(new InventoryClient.WarehouseIds(ids)).data();
        } catch (FeignException ex) {
            log.error("Không kiểm được kho ở inventory-service", ex);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Chưa kiểm được kho, vui lòng thử lại sau");
        }
        if (res == null) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Chưa kiểm được kho, vui lòng thử lại sau");
        }
        List<Integer> missing = res.missing() == null ? List.of() : res.missing();
        List<Integer> closed = res.closed() == null ? List.of() : res.closed();
        if (!missing.isEmpty() || !closed.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Kho không tồn tại hoặc đã ngừng hoạt động",
                    Map.of("missing", missing, "closed", closed));
        }
    }
}
