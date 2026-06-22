package com.getcloudledger.api.account.application.getaccount;

import com.getcloudledger.api.shared.domain.bus.query.Query;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class GetAccountQuery implements Query {
    private final UUID accountId;
}
