package com.microinvent.tenant.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microinvent.common.event.DomainEvent;
import com.microinvent.common.messaging.EventPublisher;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-20T03:00:00Z"), ZoneOffset.UTC);

    private JdbcTemplate jdbc;
    private EventPublisher publisher;
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        publisher = mock(EventPublisher.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
        relay = new OutboxRelay(jdbc, tx, publisher, new ObjectMapper(), List.of("tigernet"), 100, 3, CLOCK);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @SuppressWarnings("unchecked")
    private void pending(OutboxRelay.Row... rows) {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(100))).thenReturn(List.of(rows));
    }

    private static OutboxRelay.Row row(long id, int retries) {
        return new OutboxRelay.Row(id, "evt-" + id, "audit.log", 5, "{\"action\":\"CREATE\"}",
                Timestamp.from(Instant.parse("2026-11-20T02:59:00Z")), retries);
    }

    @Test
    void appendOutsideTransactionIsRejected() {
        OutboxWriter writer = new OutboxWriter(jdbc, new ObjectMapper(), CLOCK);
        assertThatThrownBy(() -> writer.append("audit.log", "PRODUCT", 5, Map.of()))
                .isInstanceOf(IllegalStateException.class);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void appendInsideTransactionInsertsPendingRow() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        String id = new OutboxWriter(jdbc, new ObjectMapper(), CLOCK)
                .append("audit.log", "PRODUCT", 5, Map.of("action", "CREATE"));
        assertThat(id).hasSize(36);
        verify(jdbc).update(contains("INSERT INTO outbox_events"), eq(id), eq("audit.log"), eq("PRODUCT"), eq(5),
                eq("{\"action\":\"CREATE\"}"), eq(Timestamp.from(CLOCK.instant())));
    }

    @Test
    void publishesPendingEventsWithTenantAndMarksPublished() {
        pending(row(1, 0), row(2, 0));

        assertThat(relay.relayTenant("tigernet")).isEqualTo(2);

        ArgumentCaptor<DomainEvent> sent = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, org.mockito.Mockito.times(2)).publish(sent.capture());
        DomainEvent first = sent.getAllValues().get(0);
        assertThat(first.eventId()).isEqualTo("evt-1");
        assertThat(first.tenantId()).isEqualTo("tigernet");
        assertThat(first.payload()).containsEntry("action", "CREATE");
        verify(jdbc).update(contains("status = 'PUBLISHED'"), eq(Timestamp.from(CLOCK.instant())), eq(1L));
    }

    @Test
    void brokerDownKeepsEventPendingAndStopsBatch() {
        pending(row(1, 0), row(2, 0));
        doThrow(new AmqpConnectException(new java.net.ConnectException("refused"))).when(publisher)
                .publish(any(DomainEvent.class));

        assertThat(relay.relayTenant("tigernet")).isZero();

        verify(jdbc).update(contains("retry_count = ?"), eq(1), eq("PENDING"), anyString(), eq(1L));
        verify(publisher, org.mockito.Mockito.times(1)).publish(any(DomainEvent.class));
    }

    @Test
    void tooManyRetriesMarksFailed() {
        pending(row(1, 2));
        doThrow(new AmqpConnectException(new java.net.ConnectException("refused"))).when(publisher)
                .publish(any(DomainEvent.class));

        relay.relayTenant("tigernet");

        verify(jdbc).update(contains("retry_count = ?"), eq(3), eq("FAILED"), anyString(), eq(1L));
    }
}
