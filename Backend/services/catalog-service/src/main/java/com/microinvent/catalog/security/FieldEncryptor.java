package com.microinvent.catalog.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Mã hóa trường nhạy cảm (số tài khoản NCC, CCCD khách hàng) bằng AES-256-GCM trước khi lưu DB.
 * Dữ liệu lưu = IV 12 byte + ciphertext (kèm tag 16 byte) trong cột VARBINARY.
 */
public class FieldEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    /** @param base64Key khóa 32 byte mã hóa base64 (sinh bằng: openssl rand -base64 32) */
    public FieldEncryptor(String base64Key) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key == null ? "" : base64Key.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("CATALOG_ENCRYPTION_KEY phải là chuỗi base64", ex);
        }
        if (raw.length != 32) {
            throw new IllegalStateException("CATALOG_ENCRYPTION_KEY phải dài đúng 32 byte (AES-256), hiện là "
                    + raw.length + " byte");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public byte[] encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Không mã hóa được dữ liệu", ex);
        }
    }

    public String decrypt(byte[] data) {
        if (data == null) {
            return null;
        }
        if (data.length <= IV_LENGTH) {
            throw new IllegalStateException("Dữ liệu mã hóa không hợp lệ");
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(data, IV_LENGTH, data.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Không giải mã được dữ liệu (sai khóa?)", ex);
        }
    }

    /** Che số: chỉ giữ 4 ký tự cuối, vd 0123456789 → ******6789. */
    public static String mask(String value) {
        if (value == null) {
            return null;
        }
        int keep = Math.min(4, value.length() / 2);
        return "*".repeat(value.length() - keep) + value.substring(value.length() - keep);
    }
}
