package com.getcloudledger.api.shared.adapter.out.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox", schema = "cloudledger")
@Data
@NoArgsConstructor
public class OutboxEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    /**
     * The exact SQS wire format ({@link com.getcloudledger.api.shared.domain.bus.event.DomainEventJsonSerializer#serialize}),
     * including dynamic attributes under {@code meta}. This differs from {@code events.payload}, which
     * stores the pure {@code serializePrimitives()} projection. Captured at write time so the relay
     * publishes a byte-for-byte stable message.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;

    /**
     * W3C trace context captured when the row was written, so the outbox-poller can re-inject it
     * into the relayed SQS message and keep the distributed trace unbroken across the fallback
     * path. Null when the event was produced outside an active trace (e.g. agent not attached).
     */
    @Column(name = "traceparent")
    private String traceparent;

    @Column(name = "tracestate")
    private String tracestate;

    @Generated
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public OutboxEntity(UUID eventId, String payload, Long sequenceNumber) {
        this.eventId = eventId;
        this.payload = payload;
        this.sequenceNumber = sequenceNumber;
    }
}
