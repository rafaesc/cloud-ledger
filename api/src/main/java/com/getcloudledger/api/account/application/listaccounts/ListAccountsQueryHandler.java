package com.getcloudledger.api.account.application.listaccounts;

import com.getcloudledger.api.account.domain.port.out.AccountProjectionPort;
import com.getcloudledger.api.shared.domain.bus.query.QueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ListAccountsQueryHandler implements QueryHandler<ListAccountsQuery, ListAccountsResponse> {

    private final AccountProjectionPort projectionPort;

    @Override
    public ListAccountsResponse handle(ListAccountsQuery query) {
        var summaries = projectionPort.listByOwner(query.getOwnerId()).stream()
                .map(v -> new ListAccountsResponse.AccountSummary(
                        v.accountId(),
                        v.status(),
                        v.currency(),
                        v.openedAt()
                ))
                .toList();

        return new ListAccountsResponse(query.getOwnerId(), summaries);
    }
}
