package com.getcloudledger.api.admin.application.rebuild;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Sizes the job and persists a RUNNING row synchronously, then hands off to
 * {@link ProjectionRebuildWorker} which replays in the background — so the command handler returns
 * as soon as the row exists, and the controller's follow-up {@code GetRebuildJobQuery} can read it.
 */
@Service
@RequiredArgsConstructor
public class ProjectionRebuildService {

    private final EventReplayer eventReplayer;
    private final ProjectionRebuildWorker worker;
    private final RebuildJobStore jobStore;

    /** @param accountId single aggregate to replay, or null for a full rebuild of all aggregates. */
    public void start(UUID jobId, UUID accountId) {
        long total = eventReplayer.totalEvents(accountId);
        jobStore.create(jobId, accountId, total);
        worker.run(jobId, accountId);
    }
}
