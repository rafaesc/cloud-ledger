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
public class TransferDebited extends DomainEvent implements BalanceAware {

    private BigDecimal amount;
    private UUID counterpartAccountId;
    private UUID transferId;

    public TransferDebited(UUID aggregateId, String ownerId, BigDecimal amount,
                           UUID counterpartAccountId, UUID transferId) {
        super(aggregateId, ownerId);
        this.amount = amount;
        this.counterpartAccountId = counterpartAccountId;
        this.transferId = transferId;
    }

    public TransferDebited(UUID aggregateId, String ownerId, UUID eventId, String occurredOn,
                           Integer version, BigDecimal amount,
                           UUID counterpartAccountId, UUID transferId) {
        super(aggregateId, ownerId, eventId, occurredOn, version);
        this.amount = amount;
        this.counterpartAccountId = counterpartAccountId;
        this.transferId = transferId;
    }

    public static String eventName() {
        return "TransferDebited";
    }

    @Override
    public String eventType() {
        return eventName();
    }

    @Override
    public HashMap<String, Object> toPrimitives() {
        var primitives = new HashMap<String, Object>();
        primitives.put("amount", amount.toPlainString());
        primitives.put("counterpart_account_id", counterpartAccountId.toString());
        primitives.put("transfer_id", transferId.toString());
        return primitives;
    }

    @Override
    public TransferDebited fromPrimitives(UUID aggregateId, String ownerId, HashMap<String, Object> body,
                                          UUID eventId, String occurredOn, Integer version) {
        return new TransferDebited(
                aggregateId, ownerId, eventId, occurredOn, version,
                new BigDecimal((String) body.get("amount")),
                UUID.fromString((String) body.get("counterpart_account_id")),
                UUID.fromString((String) body.get("transfer_id")));
    }
}
