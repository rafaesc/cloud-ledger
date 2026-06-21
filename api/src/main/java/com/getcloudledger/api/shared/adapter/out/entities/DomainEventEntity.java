package com.getcloudledger.api.shared.adapter.out.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "events", schema = "cloudledger")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DomainEventEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    private UUID userId;

    private UUID aggregateId;

    private String aggregateType;

    private String eventName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload;

    @Column(nullable = false)
    private Integer version;

    private LocalDateTime occurredOn;

    @Generated()
    @Column(name = "sequence_number", insertable = false, updatable = false)
    private Long sequenceNumber;
}
