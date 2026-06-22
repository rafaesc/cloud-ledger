package com.getcloudledger.api.shared.domain.bus.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.getcloudledger.api.shared.domain.Utils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventJsonDeserializer {

    private final EventsInformation information;

    @SuppressWarnings("unchecked")
public <T extends BaseEvent> T deserialize(String body)
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException,
            InstantiationException {
        HashMap<String, Object> eventData = Utils.fromJson(body, new TypeReference<>() {});
        HashMap<String, Object> data = (HashMap<String, Object>) eventData.get("data");
        HashMap<String, Object> attributes = (HashMap<String, Object>) data.get("attributes");
        Class<? extends BaseEvent> eventClass = information.search((String) data.get("type"));

        BaseEvent nullInstance = eventClass.getConstructor().newInstance();

        Method fromPrimitivesMethod = eventClass.getMethod(
                "fromPrimitives",
                UUID.class,
                UUID.class,
                HashMap.class,
                UUID.class,
                String.class,
                Integer.class);

        return (T) fromPrimitivesMethod.invoke(
                nullInstance,
                UUID.fromString((String) attributes.get("aggregate_id")),
                UUID.fromString((String) attributes.get("user_id")),
                attributes,
                UUID.fromString((String) data.get("event_id")),
                (String) data.get("occurred_on"),
                (Integer) data.get("version"));
    }

    @SuppressWarnings("unchecked")
public <T extends BaseEvent> T deserializePrimitives(
            String eventId,
            String userId,
            String aggregateId,
            String eventName,
            String occurredOn,
            Integer version,
            String body)
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException,
            InstantiationException {
        HashMap<String, Object> attributes = Utils.fromJson(body, new TypeReference<>() {});
        Class<? extends BaseEvent> eventClass = information.search(eventName);

        BaseEvent nullInstance = eventClass.getConstructor().newInstance();

        Method fromPrimitivesMethod = eventClass.getMethod(
                "fromPrimitives",
                UUID.class,
                UUID.class,
                HashMap.class,
                UUID.class,
                String.class,
                Integer.class);

        return (T) fromPrimitivesMethod.invoke(
                nullInstance,
                UUID.fromString(aggregateId),
                UUID.fromString(userId),
                attributes,
                UUID.fromString(eventId),
                occurredOn,
                version);
    }
}
