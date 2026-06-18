package com.getcloudledger.api.account.domain.port.out;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface BalanceCache {
    void put(UUID accountId, BigDecimal balance);
    Optional<BigDecimal> get(UUID accountId);
    void evict(UUID accountId);
}
