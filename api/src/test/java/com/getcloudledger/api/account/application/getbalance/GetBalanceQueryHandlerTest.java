package com.getcloudledger.api.account.application.getbalance;

import com.getcloudledger.api.account.domain.exception.AccountNotFoundException;
import com.getcloudledger.api.account.domain.port.out.AccountProjectionPort;
import com.getcloudledger.api.account.domain.port.out.BalanceCache;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GetBalanceQueryHandler")
class GetBalanceQueryHandlerTest {

    private final AccountProjectionPort projectionPort = mock(AccountProjectionPort.class);
    private final BalanceCache balanceCache = mock(BalanceCache.class);
    private final CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test");
    private final GetBalanceQueryHandler handler = new GetBalanceQueryHandler(projectionPort, balanceCache, circuitBreaker);

    @Test
    @DisplayName("handle | uses Redis balance when cache hit, metadata from DynamoDB")
    void handle_uses_redis_balance_on_cache_hit() {
        var accountId = UUID.randomUUID();
        var balanceView = new AccountProjectionPort.AccountBalanceView(
                accountId.toString(), 300_00L, "USD", 3L, "2026-06-11T14:25:00.000Z"
        );
        when(projectionPort.findAccountBalance(accountId)).thenReturn(balanceView);
        when(balanceCache.get(accountId)).thenReturn(Optional.of(new BigDecimal("375.00")));

        var response = handler.handle(new GetBalanceQuery(accountId));

        assertEquals(375_00L, response.balanceCents());
        assertEquals("USD", response.currency());
        assertEquals(3L, response.version());
        assertEquals("2026-06-11T14:25:00.000Z", response.asOf());
    }

    @Test
    @DisplayName("handle | falls back to DynamoDB balance when cache miss")
    void handle_falls_back_to_dynamo_on_cache_miss() {
        var accountId = UUID.randomUUID();
        var balanceView = new AccountProjectionPort.AccountBalanceView(
                accountId.toString(), 500_00L, "USD", 5L, "2026-06-12T09:00:00.000Z"
        );
        when(projectionPort.findAccountBalance(accountId)).thenReturn(balanceView);
        when(balanceCache.get(accountId)).thenReturn(Optional.empty());

        var response = handler.handle(new GetBalanceQuery(accountId));

        assertEquals(500_00L, response.balanceCents());
        assertEquals(accountId.toString(), response.accountId());
    }

    @Test
    @DisplayName("handle | warms Redis with the DynamoDB balance on cache miss")
    void handle_warms_cache_on_cache_miss() {
        var accountId = UUID.randomUUID();
        var balanceView = new AccountProjectionPort.AccountBalanceView(
                accountId.toString(), 500_00L, "USD", 5L, "2026-06-12T09:00:00.000Z"
        );
        when(projectionPort.findAccountBalance(accountId)).thenReturn(balanceView);
        when(balanceCache.get(accountId)).thenReturn(Optional.empty());

        handler.handle(new GetBalanceQuery(accountId));

        verify(balanceCache).put(accountId, new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("handle | does not re-write cache on cache hit")
    void handle_does_not_write_cache_on_cache_hit() {
        var accountId = UUID.randomUUID();
        var balanceView = new AccountProjectionPort.AccountBalanceView(
                accountId.toString(), 300_00L, "USD", 3L, "2026-06-11T14:25:00.000Z"
        );
        when(projectionPort.findAccountBalance(accountId)).thenReturn(balanceView);
        when(balanceCache.get(accountId)).thenReturn(Optional.of(new BigDecimal("375.00")));

        handler.handle(new GetBalanceQuery(accountId));

        verify(balanceCache, never()).put(any(), any());
    }

    @Test
    @DisplayName("handle | propagates AccountNotFoundException when BALANCE item not found")
    void handle_propagates_not_found() {
        var accountId = UUID.randomUUID();
        when(projectionPort.findAccountBalance(accountId))
                .thenThrow(new AccountNotFoundException(accountId));
        when(balanceCache.get(accountId)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> handler.handle(new GetBalanceQuery(accountId)));
    }

    @Test
    @DisplayName("handle | falls back to DynamoDB balance when Redis throws")
    void handle_falls_back_to_dynamo_when_redis_throws() {
        var accountId = UUID.randomUUID();
        var balanceView = new AccountProjectionPort.AccountBalanceView(
                accountId.toString(), 200_00L, "USD", 2L, "2026-06-11T10:00:00.000Z"
        );
        when(projectionPort.findAccountBalance(accountId)).thenReturn(balanceView);
        when(balanceCache.get(accountId)).thenThrow(new RuntimeException("Redis connection refused"));

        var response = handler.handle(new GetBalanceQuery(accountId));

        assertEquals(200_00L, response.balanceCents());
    }
}
