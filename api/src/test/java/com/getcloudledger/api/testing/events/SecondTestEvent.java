package com.getcloudledger.api.testing.events;

import com.getcloudledger.api.shared.domain.bus.event.DomainEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class SecondTestEvent extends DomainEvent {

    private String valueObject;

    public SecondTestEvent(UUID aggregateId, String ownerId, String valueObject) {
        super(aggregateId, ownerId);
        this.valueObject = valueObject;
    }

    public SecondTestEvent(UUID aggregateId, String ownerId, UUID eventId, String occurredOn,
                           Integer version, String valueObject) {
        super(aggregateId, ownerId, eventId, occurredOn, version);
        this.valueObject = valueObject;
    }

    public static String eventName() {
        return "SecondTestEvent";
    }

    @Override
    public String eventType() {
        return eventName();
    }

    @Override
    public HashMap<String, Object> toPrimitives() {
        var primitives = new HashMap<String, Object>();
        primitives.put("value_object", valueObject);
        return primitives;
    }

    @Override
    public DomainEvent fromPrimitives(UUID aggregateId, String ownerId, HashMap<String, Object> body,
                                      UUID eventId, String occurredOn, Integer version) {
        return new SecondTestEvent(aggregateId, ownerId, eventId, occurredOn, version,
                (String) body.get("value_object"));
    }
}
