package com.getcloudledger.api.shared.domain.bus.event;

import com.getcloudledger.api.shared.domain.Utils;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

@Slf4j
public class DomainEventJsonSerializer {

    public static <T extends DomainEvent> String serialize(T domainEvent) {
        HashMap<String, Object> attributes = domainEvent.toPrimitives();
        attributes.put("aggregate_id", domainEvent.getAggregateId());
        attributes.put("account_id", domainEvent.getAccountId());

        return Utils.toJson(new HashMap<String, Object>() {
            {
                put("data", new HashMap<String, Object>() {
                    {
                        put("event_id", domainEvent.getEventId());
                        put("version", domainEvent.getVersion());
                        put("type", Utils.getEventName(domainEvent.getClass()));
                        put("occurred_on", domainEvent.getOccurredOn());
                        put("attributes", attributes);
                    }
                });
                put("meta", new HashMap<String, Object>());
            }
        });
    }

    public static <T extends DomainEvent> String serializePrimitives(T domainEvent) {
        return Utils.toJson(domainEvent.toPrimitives());
    }
}
