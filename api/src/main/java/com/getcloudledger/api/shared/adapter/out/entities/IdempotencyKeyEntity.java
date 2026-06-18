package com.getcloudledger.api.shared.adapter.out.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.OffsetDateTime;

@Entity
@Table(name = "idempotency_keys", schema = "cloudledger")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyKeyEntity implements Persistable<String> {

    @Id
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "response_status", nullable = false)
    private Integer responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "jsonb", nullable = false)
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Override
    public String getId() {
        return idempotencyKey;
    }

    /**
     * Idempotency records are insert-once and never updated, so the row is always treated as new.
     * This forces {@code save()} to {@code persist} (INSERT) rather than {@code merge} (UPDATE), so a
     * duplicate key surfaces the unique-constraint violation instead of silently overwriting.
     */
    @Override
    @Transient
    public boolean isNew() {
        return true;
    }
}
