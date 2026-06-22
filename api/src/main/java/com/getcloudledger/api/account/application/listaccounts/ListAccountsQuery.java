package com.getcloudledger.api.account.application.listaccounts;

import com.getcloudledger.api.shared.domain.bus.query.Query;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ListAccountsQuery implements Query {
    private final String ownerId;
}
