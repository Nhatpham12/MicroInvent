package com.microinvent.sales.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.math.BigDecimal;
import java.util.Arrays;

/** Thuế suất VAT – lưu đúng giá trị ENUM('NONE','0','5','8','10') của DB, JSON cũng dùng các giá trị này. */
public enum VatRate {
    NONE("NONE", 0), V0("0", 0), V5("5", 5), V8("8", 8), V10("10", 10);

    private final String code;
    private final BigDecimal percent;

    VatRate(String code, int percent) {
        this.code = code;
        this.percent = BigDecimal.valueOf(percent);
    }

    @JsonValue
    public String code() {
        return code;
    }

    public BigDecimal percent() {
        return percent;
    }

    @JsonCreator
    public static VatRate of(String code) {
        return Arrays.stream(values()).filter(v -> v.code.equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("vatRate chỉ nhận NONE, 0, 5, 8, 10"));
    }

    @Converter(autoApply = true)
    public static class DbConverter implements AttributeConverter<VatRate, String> {
        @Override
        public String convertToDatabaseColumn(VatRate v) {
            return v == null ? null : v.code;
        }

        @Override
        public VatRate convertToEntityAttribute(String code) {
            return code == null ? null : of(code);
        }
    }
}
