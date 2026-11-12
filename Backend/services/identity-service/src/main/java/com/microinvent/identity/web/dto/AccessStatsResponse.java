package com.microinvent.identity.web.dto;

import java.util.List;

/** Lượng truy cập theo tháng trong năm (đủ 12 tháng, tháng không có dữ liệu = 0). */
public record AccessStatsResponse(int year, List<Month> months) {

    /** activeUsers = số người dùng khác nhau đăng nhập thành công trong tháng. */
    public record Month(int month, long successCount, long failedCount, long activeUsers) {
    }
}
