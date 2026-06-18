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
public class TransferFailed extends DomainEvent implements BalanceAware {

    private BigDecimal amount;
    private UUID counterpartAccountId;
    private UUID transferId;
    private String reason;

    public TransferFailed(UUID aggregateId, UUID accountId, BigDecimal amount,
                          UUID counterpartAccountId, UUID transferId, String reason) {
        super(aggregateId, accountId);
        this.amount = amount;
        this.counterpartAccountId = counterpartAccountId;
        this.transferId = transferId;
        this.reason = reason;
    }

    public TransferFailed(UUID aggregateId, UUID accountId, UUID eventId, String occurredOn,
                          Integer version, BigDecimal amount,
                          UUID counterpartAccountId, UUID transferId, String reason) {
        super(aggregateId, accountId, eventId, occurredOn, version);
        this.amount = amount;
        this.counterpartAccountId = counterpartAccountId;
        this.transferId = transferId;
        this.reason = reason;
    }

    public static String eventName() {
        return "transfer-failed";
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
        primitives.put("reason", reason);
        return primitives;
    }

    @Override
    public TransferFailed fromPrimitives(UUID aggregateId, UUID accountId, HashMap<String, Object> body,
                                         UUID eventId, String occurredOn, Integer version) {
        return new TransferFailed(
                aggregateId, accountId, eventId, occurredOn, version,
                new BigDecimal((String) body.get("amount")),
                UUID.fromString((String) body.get("counterpart_account_id")),
                UUID.fromString((String) body.get("transfer_id")),
                (String) body.get("reason"));
    }
}
