package com.getcloudledger.api.account.application.gettransactions;

import com.getcloudledger.api.shared.domain.bus.query.Query;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class GetTransactionsQuery implements Query {
    private final UUID accountId;
    private final int limit;
    private final String cursor;
    private final boolean ascending;
}
