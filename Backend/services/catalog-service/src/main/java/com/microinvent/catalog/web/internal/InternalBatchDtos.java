package com.microinvent.catalog.web.internal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** DTO cho API nội bộ lấy danh mục theo danh sách id (snapshot vào chứng từ). */
public final class InternalBatchDtos {

    public static final int MAX_IDS = 500;

    private InternalBatchDtos() {
    }

    public record ProductBatchRequest(
            @NotNull(message = "Thiếu ids") @Size(max = MAX_IDS, message = "Tối đa 500 id mỗi lần") List<Integer> ids) {
    }

    /** @param deleted true = sản phẩm đã xóa mềm (chứng từ cũ vẫn tra được) */
    public record ProductSnapshot(Integer id, String code, String name, String unit, Integer groupId,
            String groupCode, String groupName, BigDecimal defaultPriceIn, BigDecimal defaultPriceOut,
            BigDecimal defaultRentPricePerDay, Integer minStock, boolean hasSerial, Integer warrantyMonths,
            boolean deleted) {
    }

    public record PartnerBatchRequest(
            @Size(max = MAX_IDS, message = "Tối đa 500 id mỗi lần") List<Integer> supplierIds,
            @Size(max = MAX_IDS, message = "Tối đa 500 id mỗi lần") List<Integer> customerIds) {
    }

    /** Không trả số tài khoản / CCCD (service khác không cần). */
    public record SupplierSnapshot(Integer id, String code, String name, String taxCode, String address,
            String phoneFax, String email, boolean deleted) {
    }

    public record CustomerSnapshot(Integer id, String code, String name, String customerType, String taxCode,
            String address, String phone, String email, boolean deleted) {
    }

    /** @param notFound id không tồn tại (để service gọi báo lỗi rõ ràng) */
    public record PartnerBatchResponse(List<SupplierSnapshot> suppliers, List<CustomerSnapshot> customers,
            List<Integer> supplierIdsNotFound, List<Integer> customerIdsNotFound) {
    }

    public record ProductBatchResponse(List<ProductSnapshot> products, List<Integer> notFound) {
    }
}
