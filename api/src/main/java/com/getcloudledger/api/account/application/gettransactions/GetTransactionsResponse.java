package com.getcloudledger.api.account.application.gettransactions;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GetTransactionsResponse(
        String accountId,
        List<TransactionItem> items,
        String nextCursor
) {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TransactionItem(
            long sequenceNumber,
            String eventType,
            String direction,
            long amountCents,
            String counterpartAccountId,
            String transferId,
            String eventId,
            String occurredAt
    ) {}
}
