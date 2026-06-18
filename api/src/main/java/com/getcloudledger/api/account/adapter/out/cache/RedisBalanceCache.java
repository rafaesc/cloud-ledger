package com.getcloudledger.api.account.adapter.out.cache;

import com.getcloudledger.api.account.domain.port.out.BalanceCache;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RedisBalanceCache implements BalanceCache {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final String KEY_PREFIX = "balance:";

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void put(UUID accountId, BigDecimal balance) {
        redisTemplate.opsForValue().set(KEY_PREFIX + accountId, balance.toPlainString(), TTL);
    }

    @Override
    public Optional<BigDecimal> get(UUID accountId) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + accountId);
        return Optional.ofNullable(value).map(BigDecimal::new);
    }

    @Override
    public void evict(UUID accountId) {
        redisTemplate.delete(KEY_PREFIX + accountId);
    }
}
