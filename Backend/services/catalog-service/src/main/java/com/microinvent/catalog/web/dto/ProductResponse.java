package com.microinvent.catalog.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(Integer id, String code, String name, Integer groupId, String groupCode,
        String groupName, String unit, BigDecimal defaultPriceIn, BigDecimal defaultPriceOut,
        BigDecimal defaultRentPricePerDay, Integer minStock, boolean hasSerial, Integer warrantyMonths,
        String imageUrl, String description, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
