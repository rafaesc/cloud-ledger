package com.getcloudledger.api.account.application.listaccounts;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ListAccountsResponse(
        String ownerId,
        List<AccountSummary> items
) {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AccountSummary(
            String accountId,
            String status,
            String currency,
            String openedAt
    ) {}
}
