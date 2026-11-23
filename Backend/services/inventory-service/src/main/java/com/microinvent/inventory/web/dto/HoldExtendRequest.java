package com.microinvent.inventory.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/** Gia hạn giữ hàng tới thời điểm mới (phải sau hạn hiện tại và sau thời điểm hiện tại). */
public record HoldExtendRequest(@NotNull(message = "Vui lòng chọn hạn giữ mới") LocalDateTime expiresAt) {
}
