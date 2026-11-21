package com.microinvent.procurement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Event chờ gửi RabbitMQ – ghi trong cùng transaction với chứng từ, OutboxPublisherJob đẩy đi sau. */
@Entity
@Table(name = "outbox_events")
@Getter
@Setter
public class OutboxEvent {

    public enum Status { PENDING, PUBLISHED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** UUID cố định – gửi lại vẫn cùng id để consumer chống trùng. Cột CHAR(36). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false)
    private Integer aggregateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;
}
