package com.getcloudledger.api.account.application.getbalance;

import com.getcloudledger.api.account.domain.port.out.AccountProjectionPort;
import com.getcloudledger.api.account.domain.port.out.BalanceCache;
import com.getcloudledger.api.shared.domain.bus.query.QueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class GetBalanceQueryHandler implements QueryHandler<GetBalanceQuery, GetBalanceResponse> {

    private final AccountProjectionPort projectionPort;
    private final BalanceCache balanceCache;

    @Override
    public GetBalanceResponse handle(GetBalanceQuery query) {
        var balanceView = projectionPort.findAccountBalance(query.getAccountId());

        long balanceCents = balanceCache.get(query.getAccountId())
                .map(b -> b.multiply(BigDecimal.valueOf(100)).longValue())
                .orElse(balanceView.balanceCents());

        return new GetBalanceResponse(
                balanceView.accountId(),
                balanceCents,
                balanceView.currency(),
                balanceView.version(),
                balanceView.updatedAt()
        );
    }
}
