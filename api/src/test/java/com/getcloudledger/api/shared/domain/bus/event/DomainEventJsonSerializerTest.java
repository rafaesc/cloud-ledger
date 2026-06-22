package com.getcloudledger.api.shared.domain.bus.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.getcloudledger.api.shared.domain.Utils;
import com.getcloudledger.api.testing.events.FirstTestEvent;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainEventJsonSerializerTest {

    @Test
    void serialize_includes_expected_fields() {
        FirstTestEvent event = new FirstTestEvent(UUID.randomUUID(), UUID.randomUUID());

        String json = DomainEventJsonSerializer.serialize(event);

        HashMap<String, Serializable> eventData = Utils.fromJson(json, new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        HashMap<String, Serializable> data = (HashMap<String, Serializable>) eventData.get("data");
        @SuppressWarnings("unchecked")
        HashMap<String, Serializable> attributes = (HashMap<String, Serializable>) data.get("attributes");

        assertNotNull(data);
        assertNotNull(attributes);

        assertEquals(FirstTestEvent.eventName(), data.get("type"));
        assertEquals(event.getEventId().toString(), data.get("event_id"));
        assertEquals(event.getOccurredOn(), data.get("occurred_on"));

        assertEquals(event.getAggregateId().toString(), attributes.get("aggregate_id"));
        assertEquals(event.getUserId().toString(), attributes.get("user_id"));
    }

    @Test
    void serialize_exposes_dynamic_attributes_under_meta() {
        FirstTestEvent event = new FirstTestEvent(UUID.randomUUID(), UUID.randomUUID());
        event.putDynamicAttribute("balance_after", "150.00");

        String json = DomainEventJsonSerializer.serialize(event);

        HashMap<String, Serializable> eventData = Utils.fromJson(json, new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        HashMap<String, Serializable> meta = (HashMap<String, Serializable>) eventData.get("meta");
        @SuppressWarnings("unchecked")
        HashMap<String, Serializable> attributes =
                (HashMap<String, Serializable>) ((HashMap<String, Serializable>) eventData.get("data")).get("attributes");

        assertEquals("150.00", meta.get("balance_after"), "dynamic attribute must surface under meta");
        assertFalse(attributes.containsKey("balance_after"), "dynamic attribute must not leak into the event's intrinsic attributes");
    }

    @Test
    void serializePrimitives_omits_dynamic_attributes() {
        FirstTestEvent event = new FirstTestEvent(UUID.randomUUID(), UUID.randomUUID());
        event.putDynamicAttribute("balance_after", "150.00");

        String json = DomainEventJsonSerializer.serializePrimitives(event);

        assertFalse(json.contains("balance_after"),
                "the immutable event-store payload must never carry derived dynamic attributes");
        assertTrue(event.getDynamicAttributes().containsKey("balance_after"),
                "the attribute still lives on the in-memory event for the wire format");
    }
}
