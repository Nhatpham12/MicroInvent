package com.microinvent.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microinvent.common.event.AuditPayload;
import com.microinvent.common.event.DomainEvent;
import com.microinvent.common.event.EventTypes;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditLogWriterTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AuditLogWriter writer = new AuditLogWriter(jdbc, new ObjectMapper());

    private static DomainEvent event(Map<String, Object> payload) {
        return DomainEvent.of(EventTypes.AUDIT_LOG, "tigernet", 5, payload);
    }

    @Test
    void writesAuditRowWithJsonBeforeAfter() {
        when(jdbc.update(startsWith("INSERT IGNORE"), anyString())).thenReturn(1);
        AuditPayload p = new AuditPayload(1L, "admin", 2L, "catalog-service", "UPDATE", "PRODUCT", "5",
                Map.of("name", "A"), Map.of("name", "B"), "10.0.0.1");

        assertThat(writer.write(event(p.toMap()))).isTrue();

        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(startsWith("INSERT INTO audit_logs"), args.capture(), args.capture(), args.capture(),
                args.capture(), args.capture(), args.capture(), args.capture(), args.capture(), args.capture(),
                args.capture(), args.capture());
        assertThat(args.getAllValues()).startsWith(1, "admin", 2, "catalog-service", "UPDATE", "PRODUCT", "5",
                "{\"name\":\"A\"}", "{\"name\":\"B\"}", "10.0.0.1");
    }

    @Test
    void skipsDuplicateEvent() {
        when(jdbc.update(startsWith("INSERT IGNORE"), anyString())).thenReturn(0);
        assertThat(writer.write(event(Map.of("action", "X", "entity", "Y")))).isFalse();
        verify(jdbc, never()).update(startsWith("INSERT INTO audit_logs"), any(Object[].class));
    }

    @Test
    void rejectsPayloadWithoutAction() {
        when(jdbc.update(startsWith("INSERT IGNORE"), eq("e"))).thenReturn(1);
        DomainEvent e = new DomainEvent("e", EventTypes.AUDIT_LOG, "tigernet", "1", null, Map.of("entity", "Y"));
        assertThatThrownBy(() -> writer.write(e)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("action");
    }

    @Test
    void truncateLongValues() {
        assertThat(AuditLogWriter.truncate("abcdef", 3)).isEqualTo("abc");
        assertThat(AuditLogWriter.truncate(null, 3)).isNull();
    }
}
