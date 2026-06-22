package com.getcloudledger.api.account.application.getbalance;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GetBalanceResponse(
        String accountId,
        long balanceCents,
        String currency,
        long version,
        String asOf
) {}
