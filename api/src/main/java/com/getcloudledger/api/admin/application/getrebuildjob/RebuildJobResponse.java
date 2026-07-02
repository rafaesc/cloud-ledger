package com.getcloudledger.api.admin.application.getrebuildjob;

import com.getcloudledger.api.admin.adapter.out.persistence.RebuildJobEntity;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read-side wire format for a projection-rebuild job. Snake_case via Jackson 3.x
 * ({@code tools.jackson.*}) to match every other GET response — see the Jackson 3.x note in
 * CLAUDE.md. {@code error} is null unless the job FAILED; {@code finishedAt} is null while RUNNING.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RebuildJobResponse(
        UUID jobId,
        UUID accountId,
        String status,
        long totalEvents,
        long processedEvents,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String error) {

    public static RebuildJobResponse from(RebuildJobEntity job) {
        return new RebuildJobResponse(
                job.getId(),
                job.getAccountId(),
                job.getStatus(),
                job.getTotalEvents(),
                job.getProcessedEvents(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getError());
    }
}
