package com.getcloudledger.api.shared.domain.bus.event;

import java.util.HashMap;
import java.util.UUID;

public abstract class IntegrationEvent extends BaseEvent {

    public IntegrationEvent(UUID aggregateId, String ownerId, UUID eventId,
                            String occurredOn, Integer version) {
        super(aggregateId, ownerId, eventId, occurredOn, version);
    }

    protected IntegrationEvent() {
        super();
    }

    public abstract IntegrationEvent fromPrimitives(
            UUID aggregateId,
            String ownerId,
            HashMap<String, Object> body,
            UUID eventId,
            String occurredOn,
            Integer version);
}
