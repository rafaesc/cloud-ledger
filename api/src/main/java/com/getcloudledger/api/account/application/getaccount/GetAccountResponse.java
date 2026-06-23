package com.getcloudledger.api.account.application.getaccount;

import tools.jackson.databind.annotation.JsonNaming;
import tools.jackson.databind.PropertyNamingStrategies;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GetAccountResponse(
        String accountId,
        String ownerId,
        String status,
        String currency,
        long version,
        String openedAt,
        String frozenAt,
        String closedAt
) {}
