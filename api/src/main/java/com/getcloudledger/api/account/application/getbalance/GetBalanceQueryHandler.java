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
                .orElseGet(() -> {
                    long fallbackCents = balanceView.balanceCents();
                    warmCache(query.getAccountId(), fallbackCents);
                    return fallbackCents;
                });

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

    private void warmCache(UUID accountId, long balanceCents) {
        try {
            CircuitBreaker.decorateRunnable(balanceCacheCircuitBreaker,
                    () -> balanceCache.put(accountId, BigDecimal.valueOf(balanceCents, 2))).run();
        } catch (Exception e) {
            // best-effort cache warming; a Redis failure must never break the read path
        }
    }
}
