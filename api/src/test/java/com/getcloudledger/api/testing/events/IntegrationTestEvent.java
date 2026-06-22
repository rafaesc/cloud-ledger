package com.getcloudledger.api.testing.events;

import com.getcloudledger.api.shared.domain.bus.event.IntegrationEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class IntegrationTestEvent extends IntegrationEvent {

    private String valueObject;

    public IntegrationTestEvent(UUID aggregateId, String ownerId, UUID eventId, String occurredOn,
                                Integer version, String valueObject) {
        super(aggregateId, ownerId, eventId, occurredOn, version);
        this.valueObject = valueObject;
    }

    public static String eventName() {
        return "IntegrationTestEvent";
    }

    @Override
    public IntegrationEvent fromPrimitives(UUID aggregateId, String ownerId, HashMap<String, Object> body,
                                           UUID eventId, String occurredOn, Integer version) {
        return new IntegrationTestEvent(aggregateId, ownerId, eventId, occurredOn, version,
                (String) body.get("value_object"));
    }
}
