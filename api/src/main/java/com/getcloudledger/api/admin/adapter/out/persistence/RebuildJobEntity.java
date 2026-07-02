package com.getcloudledger.api.admin.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rebuild_jobs", schema = "cloudledger")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RebuildJobEntity {

    @Id
    private UUID id;

    /** Null means a full rebuild across all aggregates; otherwise the single aggregate replayed. */
    @Column(name = "account_id")
    private UUID accountId;

    private String status;

    @Column(name = "total_events", nullable = false)
    private long totalEvents;

    @Column(name = "processed_events", nullable = false)
    private long processedEvents;

    private String error;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
