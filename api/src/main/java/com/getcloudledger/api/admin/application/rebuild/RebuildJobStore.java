package com.getcloudledger.api.admin.application.rebuild;

import com.getcloudledger.api.admin.adapter.out.persistence.JpaRebuildJobRepository;
import com.getcloudledger.api.admin.adapter.out.persistence.RebuildJobEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static java.time.ZoneOffset.UTC;

/**
 * Persists rebuild-job lifecycle in short, independently committed transactions. The background
 * worker runs without an enclosing transaction, so every method here commits on return — that is
 * what makes intermediate {@code processed_events} progress visible to a concurrent poller.
 * REQUIRES_NEW keeps each write self-contained even if a caller ever wraps a transaction around it.
 */
@Service
@RequiredArgsConstructor
public class RebuildJobStore {

    private final JpaRebuildJobRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RebuildJobEntity create(UUID jobId, UUID accountId, long totalEvents) {
        var job = new RebuildJobEntity(
                jobId,
                accountId,
                RebuildStatus.RUNNING.name(),
                totalEvents,
                0L,
                null,
                LocalDateTime.now(UTC),
                null);
        return repository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementProcessed(UUID jobId, long delta) {
        repository.incrementProcessed(jobId, delta);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDone(UUID jobId) {
        repository.findById(jobId).ifPresent(job -> {
            job.setStatus(RebuildStatus.DONE.name());
            job.setFinishedAt(LocalDateTime.now(UTC));
            repository.save(job);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID jobId, String error) {
        repository.findById(jobId).ifPresent(job -> {
            job.setStatus(RebuildStatus.FAILED.name());
            job.setError(error);
            job.setFinishedAt(LocalDateTime.now(UTC));
            repository.save(job);
        });
    }

    @Transactional(readOnly = true)
    public Optional<RebuildJobEntity> find(UUID jobId) {
        return repository.findById(jobId);
    }
}
