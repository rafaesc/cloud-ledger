package com.getcloudledger.api.account.domain.event;

import com.getcloudledger.api.shared.domain.bus.event.DeactivationDomainEvent;
import com.getcloudledger.api.shared.domain.bus.event.DomainEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class AccountClosed extends DomainEvent implements DeactivationDomainEvent {

    public AccountClosed(UUID aggregateId, UUID userId) {
        super(aggregateId, userId);
    }

    public AccountClosed(UUID aggregateId, UUID userId, UUID eventId, String occurredOn, Integer version) {
        super(aggregateId, userId, eventId, occurredOn, version);
    }

    public static String eventName() {
        return "AccountClosed";
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
    public AccountClosed fromPrimitives(UUID aggregateId, UUID userId, HashMap<String, Object> body,
                                        UUID eventId, String occurredOn, Integer version) {
        return new AccountClosed(aggregateId, userId, eventId, occurredOn, version);
    }
}
