package com.microinvent.identity.web.dto;

/** Quy tắc mật khẩu dùng chung cho tạo user / đổi / đặt lại mật khẩu: 8–100 ký tự, có chữ và số. */
public final class PasswordPolicy {

    public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,100}$";
    public static final String MESSAGE = "Mật khẩu 8–100 ký tự, gồm cả chữ và số";

    private PasswordPolicy() {
    }
}
