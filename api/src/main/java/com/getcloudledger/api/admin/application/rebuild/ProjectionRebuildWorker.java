package com.getcloudledger.api.admin.application.rebuild;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Runs the actual replay off the request thread. Deliberately not {@code @Transactional}: the store
 * commits progress in its own short transactions so a poller sees {@code processed_events} advance,
 * and a very long full rebuild must never hold one connection/transaction open for its duration.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectionRebuildWorker {

    private final EventReplayer eventReplayer;
    private final RebuildJobStore jobStore;

    @Async("rebuildExecutor")
    public void run(UUID jobId, UUID accountId) {
        log.info("Rebuild job {} started (accountId={})", jobId, accountId);
        try {
            eventReplayer.replay(jobId, accountId);
            jobStore.markDone(jobId);
            log.info("Rebuild job {} finished", jobId);
        } catch (Exception e) {
            log.error("Rebuild job {} failed", jobId, e);
            jobStore.markFailed(jobId, truncate(e.toString()));
        }
    }

    private String truncate(String error) {
        return error != null && error.length() > 4000 ? error.substring(0, 4000) : error;
    }
}
