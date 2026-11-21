package com.microinvent.catalog.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

public class FieldEncryptorTest {

    public static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void roundTripUsesRandomIv() {
        FieldEncryptor enc = new FieldEncryptor(KEY);
        byte[] a = enc.encrypt("0123456789");
        byte[] b = enc.encrypt("0123456789");
        assertThat(a).isNotEqualTo(b);
        assertThat(enc.decrypt(a)).isEqualTo("0123456789");
        assertThat(enc.encrypt(null)).isNull();
        assertThat(enc.decrypt(null)).isNull();
    }

    @Test
    void wrongKeyOrTamperedDataFails() {
        byte[] data = new FieldEncryptor(KEY).encrypt("0123456789");
        byte[] other = new byte[32];
        other[0] = 1;
        FieldEncryptor wrong = new FieldEncryptor(Base64.getEncoder().encodeToString(other));
        assertThatThrownBy(() -> wrong.decrypt(data)).isInstanceOf(IllegalStateException.class);
        data[data.length - 1] ^= 1;
        assertThatThrownBy(() -> new FieldEncryptor(KEY).decrypt(data)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsBadKeys() {
        assertThatThrownBy(() -> new FieldEncryptor(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new FieldEncryptor("không-phải-base64")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new FieldEncryptor(Base64.getEncoder().encodeToString(new byte[16])))
                .hasMessageContaining("32 byte");
    }

    @Test
    void maskKeepsLastFourDigits() {
        assertThat(FieldEncryptor.mask("0123456789")).isEqualTo("******6789");
        assertThat(FieldEncryptor.mask("123")).isEqualTo("**3");
        assertThat(FieldEncryptor.mask(null)).isNull();
    }
}
