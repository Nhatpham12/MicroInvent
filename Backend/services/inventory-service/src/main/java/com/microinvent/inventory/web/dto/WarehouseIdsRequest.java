package com.microinvent.inventory.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Danh sách id kho cần kiểm (API nội bộ, tối đa 500 id). */
public record WarehouseIdsRequest(@NotNull @Size(max = 500) List<@NotNull Integer> ids) {
}
