package com.getcloudledger.api.testing.events;

import com.getcloudledger.api.shared.domain.bus.event.DomainEvent;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.UUID;

@NoArgsConstructor
public class FirstTestEvent extends DomainEvent {

    public FirstTestEvent(UUID aggregateId, String ownerId) {
        super(aggregateId, ownerId);
    }

    public FirstTestEvent(UUID aggregateId, String ownerId, UUID eventId, String occurredOn, Integer version) {
        super(aggregateId, ownerId, eventId, occurredOn, version);
    }

    public static String eventName() {
        return "FirstTestEvent";
    }

    @Override
    public String eventType() {
        return eventName();
    }

    @Override
    public HashMap<String, Object> toPrimitives() {
        return new HashMap<>();
    }

    @Override
    public DomainEvent fromPrimitives(UUID aggregateId, String ownerId, HashMap<String, Object> body,
                                      UUID eventId, String occurredOn, Integer version) {
        return new FirstTestEvent(aggregateId, ownerId, eventId, occurredOn, version);
    }
}
