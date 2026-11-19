package com.microinvent.catalog.web.dto;

import com.microinvent.catalog.domain.Customer;
import java.time.LocalDateTime;

/** @param idCardNo đầy đủ ở màn chi tiết với ADMIN/MANAGER/ACCOUNTANT, còn lại bị che */
public record CustomerResponse(Integer id, String code, String name, Customer.Type customerType, String taxCode,
        String idCardNo, boolean idCardMasked, String address, String phone, String email, String note,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
}
