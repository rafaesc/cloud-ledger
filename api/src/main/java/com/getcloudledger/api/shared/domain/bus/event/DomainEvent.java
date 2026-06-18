package com.getcloudledger.api.shared.domain.bus.event;

import java.util.HashMap;
import java.util.UUID;

public abstract class DomainEvent extends BaseEvent {

    public DomainEvent(UUID aggregateId, UUID accountId) {
        super(aggregateId, accountId);
    }

    public DomainEvent(
            UUID aggregateId,
            UUID accountId,
            UUID eventId,
            String occurredOn,
            Integer version) {
        super(aggregateId, accountId, eventId, occurredOn, version);
    }

    protected DomainEvent() {
        super();
    }

    public DomainEvent fromPrimitives(
            UUID aggregateId,
            UUID accountId,
            HashMap<String, Object> body,
            UUID eventId,
            String occurredOn,
            Integer version) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public abstract String eventType();
}
