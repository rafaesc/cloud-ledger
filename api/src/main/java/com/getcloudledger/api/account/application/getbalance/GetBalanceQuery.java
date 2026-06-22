package com.getcloudledger.api.account.application.getbalance;

import com.getcloudledger.api.shared.domain.bus.query.Query;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class GetBalanceQuery implements Query {
    private final UUID accountId;
}
