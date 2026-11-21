package com.microinvent.catalog.service;

import java.util.UUID;
import java.util.function.Predicate;

/** Sinh mã tự động cho NCC / khách hàng khi người dùng bỏ trống mã: NCC00001, KH00001... */
final class PartnerCodes {

    private PartnerCodes() {
    }

    /** Mã tạm (duy nhất) để INSERT lấy id trước, sau đó thay bằng {@link #fromId}. */
    static String temporary() {
        return "~" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    /** prefix + id 5 chữ số; nếu trùng mã người dùng đã tự đặt thì thêm hậu tố -1, -2... */
    static String fromId(String prefix, Integer id, Predicate<String> exists) {
        String base = prefix + String.format("%05d", id);
        String code = base;
        for (int i = 1; exists.test(code); i++) {
            code = base + "-" + i;
        }
        return code;
    }
}
