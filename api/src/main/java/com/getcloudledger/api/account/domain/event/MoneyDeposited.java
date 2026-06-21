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
public class MoneyDeposited extends DomainEvent implements BalanceAware {

    private BigDecimal amount;

    public MoneyDeposited(UUID aggregateId, UUID userId, BigDecimal amount) {
        super(aggregateId, userId);
        this.amount = amount;
    }

    public MoneyDeposited(UUID aggregateId, UUID userId, UUID eventId, String occurredOn,
                          Integer version, BigDecimal amount) {
        super(aggregateId, userId, eventId, occurredOn, version);
        this.amount = amount;
    }

    public static String eventName() {
        return "MoneyDeposited";
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
    public MoneyDeposited fromPrimitives(UUID aggregateId, UUID userId, HashMap<String, Object> body,
                                         UUID eventId, String occurredOn, Integer version) {
        return new MoneyDeposited(
                aggregateId, userId, eventId, occurredOn, version,
                new BigDecimal((String) body.get("amount")));
    }
}
