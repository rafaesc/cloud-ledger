package com.getcloudledger.api.account.domain.event;

import com.getcloudledger.api.shared.domain.bus.event.DomainEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class MoneyWithdrawn extends DomainEvent {

    private BigDecimal amount;

    public MoneyWithdrawn(UUID aggregateId, UUID accountId, BigDecimal amount) {
        super(aggregateId, accountId);
        this.amount = amount;
    }

    public MoneyWithdrawn(UUID aggregateId, UUID accountId, UUID eventId, String occurredOn,
                          Integer version, BigDecimal amount) {
        super(aggregateId, accountId, eventId, occurredOn, version);
        this.amount = amount;
    }

    public static String eventName() {
        return "money-withdrawn";
    }

    @Override
    public String eventType() {
        return eventName();
    }

    @Override
    public HashMap<String, Object> toPrimitives() {
        var primitives = new HashMap<String, Object>();
        primitives.put("amount", amount.toPlainString());
        return primitives;
    }

    @Override
    public MoneyWithdrawn fromPrimitives(UUID aggregateId, UUID accountId, HashMap<String, Object> body,
                                         UUID eventId, String occurredOn, Integer version) {
        return new MoneyWithdrawn(
                aggregateId, accountId, eventId, occurredOn, version,
                new BigDecimal((String) body.get("amount")));
    }
}
