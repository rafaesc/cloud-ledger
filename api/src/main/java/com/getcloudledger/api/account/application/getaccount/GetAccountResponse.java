package com.getcloudledger.api.account.application.getaccount;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

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
