package com.getcloudledger.api.admin.application.rebuild;

import com.getcloudledger.api.account.domain.event.BalanceAware;
import com.getcloudledger.api.account.domain.model.Account;
import com.getcloudledger.api.shared.adapter.out.entities.DomainEventEntity;
import com.getcloudledger.api.shared.adapter.out.repository.JpaDomainEventRepository;
import com.getcloudledger.api.shared.adapter.out.sqs.SqsEventBus;
import com.getcloudledger.api.shared.domain.Utils;
import com.getcloudledger.api.shared.domain.bus.event.DomainEvent;
import com.getcloudledger.api.shared.domain.bus.event.EventJsonDeserializer;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Re-reads persisted events from Aurora and re-publishes them to SQS so the projector rebuilds the
 * DynamoDB read model. Events are streamed in bounded keyset pages — a single aggregate's stream (or
 * the full event log) is never fully materialised in Java memory; each page is published then
 * released before the next is fetched.
 *
 * <p>Each aggregate is replayed through a fresh {@link Account} so the running balance can be
 * recomputed and re-stamped as {@code balance_after} on {@link BalanceAware} events — exactly as
 * {@code BalanceAfterEnricher} does on the write path — because {@code balance_after} lives only on
 * the SQS wire format, never in the immutable event payload. The reconstructed {@code sequenceNumber}
 * is carried over from the stored row so the projector's {@code TXNS#<seq>} sort key is preserved.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventReplayer {

    private static final int EVENT_BATCH_SIZE = 500;
    private static final int AGGREGATE_PAGE_SIZE = 500;
    private static final UUID ZERO_UUID = new UUID(0L, 0L);
    private static final String ACCOUNT_AGGREGATE_TYPE = "account";

    private final JpaDomainEventRepository eventRepository;
    private final EventJsonDeserializer deserializer;
    // Concrete SqsEventBus (not the EventBus interface): publishing straight to SQS deliberately
    // bypasses the @Primary SmartEventBusRouter, whose MANDATORY-propagation/outbox write path is
    // for request-scoped writes, not a background replay.
    private final SqsEventBus sqsEventBus;
    private final RebuildJobStore jobStore;

    /** Total events to be republished — the denominator for job progress. */
    public long totalEvents(UUID accountId) {
        return accountId == null ? eventRepository.count() : eventRepository.countByAggregateId(accountId);
    }

    /** Replays a single aggregate, or every aggregate when {@code accountId} is null. */
    public void replay(UUID jobId, UUID accountId) {
        if (accountId != null) {
            replayAggregate(jobId, accountId);
            return;
        }
        UUID after = ZERO_UUID;
        while (true) {
            List<UUID> ids = eventRepository.findDistinctAggregateIdsAfter(after, Limit.of(AGGREGATE_PAGE_SIZE));
            if (ids.isEmpty()) {
                return;
            }
            for (UUID id : ids) {
                replayAggregate(jobId, id);
                after = id;
            }
            if (ids.size() < AGGREGATE_PAGE_SIZE) {
                return;
            }
        }
    }

    private void replayAggregate(UUID jobId, UUID aggregateId) {
        var account = new Account();
        int lastVersion = -1;
        while (true) {
            List<DomainEventEntity> batch = eventRepository
                    .findByAggregateIdAndVersionGreaterThanOrderByVersionAsc(
                            aggregateId, lastVersion, Limit.of(EVENT_BATCH_SIZE));
            if (batch.isEmpty()) {
                return;
            }

            List<DomainEvent> toPublish = new ArrayList<>(batch.size());
            for (DomainEventEntity entity : batch) {
                DomainEvent event = reconstruct(entity);
                account.replayEvents(List.of(event));
                if (event instanceof BalanceAware) {
                    event.putDynamicAttribute("balance_after", account.getBalance().toPlainString());
                }
                toPublish.add(event);
                lastVersion = entity.getVersion();
            }

            sqsEventBus.publish(ACCOUNT_AGGREGATE_TYPE, toPublish);
            jobStore.incrementProcessed(jobId, batch.size());
            log.info("Rebuild job {} republished {} event(s) for aggregate {}",
                    jobId, batch.size(), aggregateId);

            if (batch.size() < EVENT_BATCH_SIZE) {
                return;
            }
        }
    }

    @SneakyThrows
    private DomainEvent reconstruct(DomainEventEntity entity) {
        DomainEvent event = deserializer.deserializePrimitives(
                entity.getEventId().toString(),
                entity.getOwnerId(),
                entity.getAggregateId().toString(),
                entity.getEventName(),
                Utils.dateToString(entity.getOccurredOn()),
                entity.getVersion(),
                entity.getPayload());
        event.setSequenceNumber(entity.getSequenceNumber());
        return event;
    }
}
