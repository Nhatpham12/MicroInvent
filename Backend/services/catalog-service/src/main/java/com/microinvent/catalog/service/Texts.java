package com.microinvent.catalog.service;

import java.util.Locale;
import org.springframework.util.StringUtils;

/** Tiện ích chuẩn hóa chuỗi dùng chung trong catalog-service. */
final class Texts {

    private Texts() {
    }

    static String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    static String upperCode(String s) {
        return StringUtils.hasText(s) ? s.trim().toUpperCase(Locale.ROOT) : null;
    }

    /** Bỏ khoảng trắng / dấu chấm / gạch ngang trong số tài khoản, CCCD. */
    static String digitsOnly(String s) {
        return StringUtils.hasText(s) ? s.replaceAll("[\\s.\\-]", "") : null;
    }

    /** Thoát ký tự đại diện của LIKE (ký tự thoát là !) để tìm đúng chuỗi người dùng gõ. */
    static String escapeLike(String s) {
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    static String likePattern(String keyword) {
        return "%" + escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
    }
}
