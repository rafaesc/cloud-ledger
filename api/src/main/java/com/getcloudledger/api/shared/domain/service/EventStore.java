package com.getcloudledger.api.shared.domain.service;

import com.getcloudledger.api.shared.domain.bus.event.DomainEvent;
import com.getcloudledger.api.shared.domain.bus.event.EventBus;
import com.getcloudledger.api.shared.domain.exception.ConcurrencyException;
import com.getcloudledger.api.shared.domain.port.repository.DomainEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventStore {

    private final DomainEventRepository<DomainEvent> domainEventRepository;
    private final EventBus eventBus;

    @Retryable(value = ConcurrencyException.class, maxRetries = 3, delay = 100, multiplier = 2.0)
    public void saveEvents(UUID aggregateId, String aggregateType,
                           List<? extends DomainEvent> events, int expectedVersion,
                           UUID idempotencyKey) {
        var eventStream = domainEventRepository.findAllByAggregateId(aggregateId);

        if (expectedVersion != -1 && !eventStream.isEmpty()
                && eventStream.getLast().getVersion() != expectedVersion) {
            throw new ConcurrencyException(
                    "Concurrency conflict: expected version " + expectedVersion
                    + " but found " + eventStream.getLast().getVersion());
        }

        var version = expectedVersion;
        for (var event : events) {
            version++;
            event.setVersion(version);
            domainEventRepository.save(aggregateId, aggregateType, event, idempotencyKey);
        }

        eventBus.publish(aggregateType, events);
    }

    public List<DomainEvent> getEvents(UUID aggregateId) {
        return domainEventRepository.findAllByAggregateId(aggregateId);
    }
}
