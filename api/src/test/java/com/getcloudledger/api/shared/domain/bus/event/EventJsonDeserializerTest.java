package com.getcloudledger.api.shared.domain.bus.event;

import com.getcloudledger.api.shared.domain.Utils;
import com.getcloudledger.api.testing.events.IntegrationTestEvent;
import com.getcloudledger.api.testing.events.SecondTestEvent;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventJsonDeserializerTest {

    @Test
    void deserialize_reconstructs_domain_event_instance() throws Exception {
        var valueObject = UUID.randomUUID().toString();
        SecondTestEvent original = new SecondTestEvent(UUID.randomUUID(), UUID.randomUUID().toString(), valueObject);
        String json = DomainEventJsonSerializer.serialize(original);

        EventsInformation info = new EventsInformation();
        EventJsonDeserializer deserializer = new EventJsonDeserializer(info);

        DomainEvent result = deserializer.deserialize(json);

        assertNotNull(result);
        assertInstanceOf(SecondTestEvent.class, result);
        var reconstructed = (SecondTestEvent) result;
        assertEquals(original.getAggregateId(), reconstructed.getAggregateId());
        assertEquals(original.getOwnerId(), reconstructed.getOwnerId());
        assertEquals(original.getEventId(), reconstructed.getEventId());
        assertEquals(original.getOccurredOn(), reconstructed.getOccurredOn());
        assertEquals(original.getValueObject(), reconstructed.getValueObject());
    }

    @Test
    void deserialize_reconstructs_integration_event_instance() throws Exception {
        var valueObject = UUID.randomUUID().toString();
        IntegrationTestEvent original = new IntegrationTestEvent(
                UUID.randomUUID(), UUID.randomUUID().toString(), UUID.randomUUID(),
                "2024-01-01T00:00:00", 0, valueObject);

        HashMap<String, Serializable> attributes = new HashMap<>();
        attributes.put("value_object", original.getValueObject());
        attributes.put("aggregate_id", original.getAggregateId());
        attributes.put("owner_id", original.getOwnerId());

        HashMap<String, Serializable> data = new HashMap<>();
        data.put("event_id", original.getEventId());
        data.put("version", original.getVersion());
        data.put("type", Utils.getEventName(IntegrationTestEvent.class));
        data.put("occurred_on", original.getOccurredOn());
        data.put("attributes", attributes);

        HashMap<String, Serializable> root = new HashMap<>();
        root.put("data", data);
        root.put("meta", new HashMap<>());

        String json = Utils.toJson(root);

        EventsInformation info = new EventsInformation();
        EventJsonDeserializer deserializer = new EventJsonDeserializer(info);

        BaseEvent result = deserializer.deserialize(json);

        assertNotNull(result);
        assertInstanceOf(IntegrationTestEvent.class, result);
        var reconstructed = (IntegrationTestEvent) result;
        assertEquals(original.getAggregateId(), reconstructed.getAggregateId());
        assertEquals(original.getOwnerId(), reconstructed.getOwnerId());
        assertEquals(original.getEventId(), reconstructed.getEventId());
        assertEquals(original.getOccurredOn(), reconstructed.getOccurredOn());
        assertEquals(original.getValueObject(), reconstructed.getValueObject());
    }

    @Test
    void deserialize_throws_for_unknown_event_type() {
        HashMap<String, Serializable> attributes = new HashMap<>();
        attributes.put("aggregate_id", "agg-x");
        attributes.put("owner_id", "owner-x");

        HashMap<String, Serializable> data = new HashMap<>();
        data.put("event_id", "event-x");
        data.put("type", "non.existent.event");
        data.put("occurred_on", "2024-01-01T00:00:00");
        data.put("attributes", attributes);

        HashMap<String, Serializable> root = new HashMap<>();
        root.put("data", data);
        root.put("meta", new HashMap<>());

        String json = Utils.toJson(root);

        EventsInformation info = new EventsInformation();
        EventJsonDeserializer deserializer = new EventJsonDeserializer(info);

        assertThrows(IllegalArgumentException.class, () -> deserializer.deserialize(json));
    }
}
