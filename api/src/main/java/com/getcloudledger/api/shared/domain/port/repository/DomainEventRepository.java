package com.getcloudledger.api.shared.domain.port.repository;

import com.getcloudledger.api.shared.domain.bus.event.DomainEvent;

import java.util.List;
import java.util.UUID;

public interface DomainEventRepository<T extends DomainEvent> {

    List<T> findAllByAggregateId(UUID aggregateId);

    T save(UUID aggregateId, String aggregateType, T domainEvent);
}
