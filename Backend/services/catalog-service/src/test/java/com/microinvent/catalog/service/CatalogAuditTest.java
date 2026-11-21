package com.microinvent.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.microinvent.common.context.UserContext;
import com.microinvent.common.context.UserContextHolder;
import com.microinvent.common.event.EventTypes;
import com.microinvent.tenant.outbox.OutboxWriter;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CatalogAuditTest {

    private final OutboxWriter outbox = mock(OutboxWriter.class);
    private final CatalogAudit audit = new CatalogAudit(outbox);

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    @SuppressWarnings("unchecked")
    void writesAuditEventToOutboxWithCurrentUser() {
        UserContextHolder.set(new UserContext(3L, "quanly", Set.of("MANAGER"), Set.of(), true, 2L));

        audit.record("UPDATE", "PRODUCT", 5, CatalogAudit.snapshot("name", "A"), CatalogAudit.snapshot("name", "B"));

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(outbox).append(eq(EventTypes.AUDIT_LOG), eq("PRODUCT"), eq(5), payload.capture());
        assertThat(payload.getValue()).containsEntry("userId", 3L).containsEntry("username", "quanly")
                .containsEntry("warehouseId", 2L).containsEntry("service", "catalog-service")
                .containsEntry("action", "UPDATE").containsEntry("entity", "PRODUCT")
                .containsEntry("entityId", "5").containsEntry("before", Map.of("name", "A"));
    }

    @Test
    void snapshotSkipsNullValues() {
        assertThat(CatalogAudit.snapshot("code", "X", "phone", null)).isEqualTo(Map.of("code", "X"));
    }
}
