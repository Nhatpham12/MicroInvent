package com.microinvent.procurement.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microinvent.common.event.DomainEvent;
import com.microinvent.common.messaging.EventPublisher;
import com.microinvent.procurement.domain.OutboxEvent;
import com.microinvent.procurement.repository.OutboxEventRepository;
import com.microinvent.tenant.starter.TenantProperties;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxPublisherTest {

    private OutboxEventRepository outbox;
    private EventPublisher publisher;
    private OutboxPublisher job;

    @BeforeEach
    void setUp() {
        outbox = mock(OutboxEventRepository.class);
        publisher = mock(EventPublisher.class);
        job = new OutboxPublisher(outbox, publisher, new TenantProperties(), mock(TransactionTemplate.class));
    }

    @Test
    void publishesWithStoredEventIdAndTenant() {
        OutboxEvent e = event();
        when(outbox.findByStatusOrderByIdAsc(any(), any())).thenReturn(List.of(e));

        job.publishPending("tigernet");

        ArgumentCaptor<DomainEvent> sent = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publish(sent.capture());
        assertThat(sent.getValue().eventId()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(sent.getValue().tenantId()).isEqualTo("tigernet");
        assertThat(sent.getValue().eventType()).isEqualTo("receipt.confirmed");
        assertThat(e.getStatus()).isEqualTo(OutboxEvent.Status.PUBLISHED);
        assertThat(e.getPublishedAt()).isNotNull();
    }

    @Test
    void brokerDownKeepsEventsPendingWithoutCountingRetries() {
        OutboxEvent a = event();
        OutboxEvent b = event();
        when(outbox.findByStatusOrderByIdAsc(any(), any())).thenReturn(List.of(a, b));
        doThrow(new AmqpConnectException(new RuntimeException("refused"))).when(publisher).publish(any(DomainEvent.class));

        job.publishPending("tigernet");

        verify(publisher, times(1)).publish(any(DomainEvent.class));
        assertThat(List.of(a.getStatus(), b.getStatus())).containsOnly(OutboxEvent.Status.PENDING);
        assertThat(a.getRetryCount()).isZero();
    }

    @Test
    void otherErrorsCountRetriesAndFailAfterMax() {
        OutboxEvent e = event();
        e.setRetryCount(OutboxPublisher.MAX_RETRY - 1);
        when(outbox.findByStatusOrderByIdAsc(any(), any())).thenReturn(List.of(e));
        doThrow(new IllegalStateException("bad payload")).when(publisher).publish(any(DomainEvent.class));

        job.publishPending("tigernet");

        assertThat(e.getStatus()).isEqualTo(OutboxEvent.Status.FAILED);
        assertThat(e.getLastError()).contains("bad payload");
    }

    private static OutboxEvent event() {
        OutboxEvent e = new OutboxEvent();
        e.setEventId("11111111-1111-1111-1111-111111111111");
        e.setEventType("receipt.confirmed");
        e.setAggregateId(7);
        e.setPayload(Map.of("receiptId", 7));
        e.setCreatedAt(LocalDateTime.now());
        return e;
    }
}
