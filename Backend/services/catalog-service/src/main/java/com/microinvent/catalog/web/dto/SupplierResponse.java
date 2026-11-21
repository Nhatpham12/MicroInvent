package com.microinvent.catalog.web.dto;

import java.time.LocalDateTime;

/** @param bankAccountNo đầy đủ ở màn chi tiết với ADMIN/MANAGER/ACCOUNTANT, còn lại bị che (******6789) */
public record SupplierResponse(Integer id, String code, String name, String taxCode, String address,
        String phoneFax, String email, String bankAccountNo, boolean bankAccountMasked, String bankAccountName,
        String bankName, String note, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
