package com.getcloudledger.api.shared.domain.bus.event;

import com.getcloudledger.api.shared.domain.Utils;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.UUID;

import static java.time.ZoneOffset.UTC;

public abstract class BaseEvent {

    @Getter
    private UUID aggregateId;
    @Getter
    private UUID userId;
    @Getter
    private UUID eventId;
    @Getter
    @Setter
    private String occurredOn;
    @Getter
    @Setter
    private Integer version;

    @Getter
    @Setter
    private Long sequenceNumber;

    /**
     * Out-of-band attributes computed on demand (e.g. {@code balance_after}) and emitted only on the
     * SQS wire format via {@link DomainEventJsonSerializer#serialize}. Deliberately excluded from
     * {@link #toPrimitives()} so the immutable event-store payload stays a pure projection of the event.
     */
    @Getter
    private final HashMap<String, Object> dynamicAttributes = new HashMap<>();

    public void putDynamicAttribute(String key, Object value) {
        this.dynamicAttributes.put(key, value);
    }

    protected BaseEvent(UUID aggregateId, UUID userId) {
        this.aggregateId = aggregateId;
        this.userId = userId;
        this.eventId = UUID.randomUUID();
        this.occurredOn = Utils.dateToString(LocalDateTime.now(UTC));
        this.version = -1;
    }

    protected BaseEvent(
            UUID aggregateId,
            UUID userId,
            UUID eventId,
            String occurredOn,
            Integer version) {
        this.aggregateId = aggregateId;
        this.userId = userId;
        this.eventId = eventId;
        this.occurredOn = occurredOn;
        this.version = version;
    }

    protected BaseEvent() {
    }

    public HashMap<String, Object> toPrimitives() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public BaseEvent fromPrimitives(
            UUID aggregateId,
            UUID userId,
            HashMap<String, Object> body,
            UUID eventId,
            String occurredOn,
            Integer version) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
