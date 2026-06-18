package com.getcloudledger.api.shared.adapter.out.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.getcloudledger.api.AbstractIntegrationTest;
import com.getcloudledger.api.shared.adapter.out.entities.IdempotencyKeyEntity;
import com.getcloudledger.api.shared.domain.Utils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("JpaIdempotencyKeyRepository (integration)")
class JpaIdempotencyKeyRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private JpaIdempotencyKeyRepository repository;

    @Test
    @DisplayName("save + findById | persists and retrieves an idempotency record")
    void saveAndFind_persistsAndRetrievesRecord() {
        var entity = newEntity(UUID.randomUUID().toString(), "hash-1", 201);

        repository.save(entity);

        var found = repository.findById(entity.getIdempotencyKey());
        assertTrue(found.isPresent());

        var result = found.get();
        assertEquals(entity.getIdempotencyKey(), result.getIdempotencyKey());
        assertEquals("hash-1", result.getRequestHash());
        assertEquals(201, result.getResponseStatus());
        assertEquals("{\"id\": \"abc\"}", result.getResponseBody());
    }

    @Test
    @DisplayName("findById | returns empty when key does not exist")
    void findById_returnsEmpty_whenKeyNotFound() {
        var result = repository.findById("non-existent-key");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("save | rejects duplicate idempotency key")
    void save_rejectsDuplicateKey() {
        var key = UUID.randomUUID().toString();
        repository.save(newEntity(key, "hash-1", 200));

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(newEntity(key, "hash-2", 200)));
    }

    @Test
    @DisplayName("deleteExpired | removes only entries past their TTL")
    @Transactional
    void deleteExpired_removesOnlyExpiredEntries() {
        var expiredKey = UUID.randomUUID().toString();
        var activeKey = UUID.randomUUID().toString();

        var expired = newEntity(expiredKey, "hash-exp", 200);
        expired.setExpiresAt(OffsetDateTime.now().minusHours(1));

        var active = newEntity(activeKey, "hash-act", 200);
        active.setExpiresAt(OffsetDateTime.now().plusHours(24));

        repository.save(expired);
        repository.save(active);

        repository.deleteExpired(OffsetDateTime.now());

        assertFalse(repository.findById(expiredKey).isPresent(), "expired entry must be deleted");
        assertTrue(repository.findById(activeKey).isPresent(), "active entry must be retained");
    }

    @Test
    @DisplayName("save | persists response_body as JSONB")
    void save_persistsResponseBodyAsJsonb() {
        var key = UUID.randomUUID().toString();
        var body = "{\"accountId\":\"" + UUID.randomUUID() + "\",\"balance\":\"0.00\"}";

        var entity = new IdempotencyKeyEntity(
                key, "hash-x", 201, body,
                OffsetDateTime.now(), OffsetDateTime.now().plusHours(24));

        repository.save(entity);

        var result = repository.findById(key).orElseThrow();
        // JSONB normalizes whitespace on round-trip; compare structurally rather than byte-for-byte.
        assertEquals(
                Utils.fromJson(body, new TypeReference<Map<String, Object>>() {}),
                Utils.fromJson(result.getResponseBody(), new TypeReference<Map<String, Object>>() {}));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private IdempotencyKeyEntity newEntity(String key, String requestHash, int status) {
        return new IdempotencyKeyEntity(
                key,
                requestHash,
                status,
                "{\"id\":\"abc\"}",
                OffsetDateTime.now(),
                OffsetDateTime.now().plusHours(24));
    }
}
