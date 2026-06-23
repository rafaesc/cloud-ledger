package com.getcloudledger.api.account.application.getbalance;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GetBalanceResponse(
        String accountId,
        long balanceCents,
        String currency,
        long version,
        String asOf
) {}
