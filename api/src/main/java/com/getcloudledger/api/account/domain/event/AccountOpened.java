package com.getcloudledger.api.account.domain.event;

import com.getcloudledger.api.shared.domain.bus.event.DomainEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class AccountOpened extends DomainEvent {

    private String currency;

    public AccountOpened(UUID aggregateId, UUID userId, String currency) {
        super(aggregateId, userId);
        this.currency = currency;
    }

    public AccountOpened(UUID aggregateId, UUID userId, UUID eventId, String occurredOn,
                         Integer version, String currency) {
        super(aggregateId, userId, eventId, occurredOn, version);
        this.currency = currency;
    }

    public static String eventName() {
        return "AccountOpened";
    }

    @Override
    public String eventType() {
        return eventName();
    }

    @Override
    public HashMap<String, Object> toPrimitives() {
        var primitives = new HashMap<String, Object>();
        primitives.put("currency", currency);
        return primitives;
    }

    @Override
    public AccountOpened fromPrimitives(UUID aggregateId, UUID userId, HashMap<String, Object> body,
                                        UUID eventId, String occurredOn, Integer version) {
        return new AccountOpened(
                aggregateId, userId, eventId, occurredOn, version,
                (String) body.get("currency"));
    }
}
