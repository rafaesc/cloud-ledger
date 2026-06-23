package com.getcloudledger.api.account.application.getbalance;

import com.getcloudledger.api.account.domain.port.out.AccountProjectionPort;
import com.getcloudledger.api.account.domain.port.out.BalanceCache;
import com.getcloudledger.api.shared.domain.bus.query.QueryHandler;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetBalanceQueryHandler implements QueryHandler<GetBalanceQuery, GetBalanceResponse> {

    private final AccountProjectionPort projectionPort;
    private final BalanceCache balanceCache;
    private final CircuitBreaker balanceCacheCircuitBreaker;

    @Override
    public GetBalanceResponse handle(GetBalanceQuery query) {
        var balanceView = projectionPort.findAccountBalance(query.getAccountId());

        long balanceCents = cachedBalance(query.getAccountId())
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

    private Optional<BigDecimal> cachedBalance(UUID accountId) {
        try {
            return CircuitBreaker.decorateSupplier(balanceCacheCircuitBreaker,
                    () -> balanceCache.get(accountId)).get();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
