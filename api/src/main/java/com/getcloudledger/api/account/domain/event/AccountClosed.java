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

    public AccountClosed(UUID aggregateId, String ownerId) {
        super(aggregateId, ownerId);
    }

    public AccountClosed(UUID aggregateId, String ownerId, UUID eventId, String occurredOn, Integer version) {
        super(aggregateId, ownerId, eventId, occurredOn, version);
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
    public AccountClosed fromPrimitives(UUID aggregateId, String ownerId, HashMap<String, Object> body,
                                        UUID eventId, String occurredOn, Integer version) {
        return new AccountClosed(aggregateId, ownerId, eventId, occurredOn, version);
    }
}
