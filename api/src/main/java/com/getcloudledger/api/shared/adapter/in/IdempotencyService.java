package com.getcloudledger.api.shared.adapter.in;

import com.getcloudledger.api.shared.adapter.out.entities.IdempotencyKeyEntity;
import com.getcloudledger.api.shared.adapter.out.repository.JpaIdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final JpaIdempotencyKeyRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void store(String key, String requestHash, int responseStatus, String responseBody) {
        var now = OffsetDateTime.now();
        var body = (responseBody != null && !responseBody.isBlank()) ? responseBody : "null";
        repository.save(new IdempotencyKeyEntity(key, requestHash, responseStatus, body, now, now.plusHours(24)));
    }
}
