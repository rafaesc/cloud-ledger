package com.getcloudledger.api.account.application.getaccount;

import com.getcloudledger.api.account.domain.port.out.AccountProjectionPort;
import com.getcloudledger.api.shared.domain.bus.query.QueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetAccountQueryHandler implements QueryHandler<GetAccountQuery, GetAccountResponse> {

    private final AccountProjectionPort projectionPort;

    @Override
    public GetAccountResponse handle(GetAccountQuery query) {
        var view = projectionPort.findAccountState(query.getAccountId());
        return new GetAccountResponse(
                view.accountId(),
                view.ownerId(),
                view.status(),
                view.currency(),
                view.version(),
                view.openedAt(),
                view.frozenAt(),
                view.closedAt()
        );
    }
}
