package com.getcloudledger.api.shared.domain.bus.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.getcloudledger.api.shared.domain.Utils;
import com.getcloudledger.api.testing.events.FirstTestEvent;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DomainEventJsonSerializerTest {

    @Test
    void serialize_includes_expected_fields() {
        FirstTestEvent event = new FirstTestEvent(UUID.randomUUID(), UUID.randomUUID());

        String json = DomainEventJsonSerializer.serialize(event);

        HashMap<String, Serializable> eventData = Utils.fromJson(json, new TypeReference<>() {});
        HashMap<String, Serializable> data = (HashMap<String, Serializable>) eventData.get("data");
        HashMap<String, Serializable> attributes = (HashMap<String, Serializable>) data.get("attributes");

        assertNotNull(data);
        assertNotNull(attributes);

        assertEquals(FirstTestEvent.eventName(), data.get("type"));
        assertEquals(event.getEventId().toString(), data.get("event_id"));
        assertEquals(event.getOccurredOn(), data.get("occurred_on"));

        assertEquals(event.getAggregateId().toString(), attributes.get("aggregate_id"));
        assertEquals(event.getAccountId().toString(), attributes.get("account_id"));
    }
}
