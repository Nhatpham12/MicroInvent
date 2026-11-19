package com.microinvent.catalog.web.dto;

/** Kết quả nhập sản phẩm từ Excel (chỉ trả khi toàn bộ file hợp lệ; có lỗi thì không lưu dòng nào). */
public record ProductImportResult(int totalRows, int created, int updated) {
}
