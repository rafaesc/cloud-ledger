package com.getcloudledger.api.shared.domain.bus.event;

import java.util.HashMap;
import java.util.UUID;

public abstract class DomainEvent extends BaseEvent {

    public DomainEvent(UUID aggregateId, String ownerId) {
        super(aggregateId, ownerId);
    }

    public DomainEvent(
            UUID aggregateId,
            String ownerId,
            UUID eventId,
            String occurredOn,
            Integer version) {
        super(aggregateId, ownerId, eventId, occurredOn, version);
    }

    protected DomainEvent() {
        super();
    }

    public DomainEvent fromPrimitives(
            UUID aggregateId,
            String ownerId,
            HashMap<String, Object> body,
            UUID eventId,
            String occurredOn,
            Integer version) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public abstract String eventType();
}
