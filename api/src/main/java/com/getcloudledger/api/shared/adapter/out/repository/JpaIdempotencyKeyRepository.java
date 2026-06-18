package com.getcloudledger.api.shared.adapter.out.repository;

import com.getcloudledger.api.shared.adapter.out.entities.IdempotencyKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public interface JpaIdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, String> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM IdempotencyKeyEntity e WHERE e.expiresAt < :now")
    void deleteExpired(OffsetDateTime now);
}
