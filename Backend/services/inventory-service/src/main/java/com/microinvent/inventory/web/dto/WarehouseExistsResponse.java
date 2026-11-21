package com.microinvent.inventory.web.dto;

import java.util.List;

/**
 * Kết quả kiểm kho cho service khác (identity gán kho cho người dùng).
 *
 * @param missing id không có kho nào
 * @param closed  id kho đã ngừng hoạt động (CLOSED)
 */
public record WarehouseExistsResponse(List<Integer> missing, List<Integer> closed) {
}
