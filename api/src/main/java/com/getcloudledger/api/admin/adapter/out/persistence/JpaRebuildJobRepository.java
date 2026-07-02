package com.getcloudledger.api.admin.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface JpaRebuildJobRepository extends JpaRepository<RebuildJobEntity, UUID> {

    /**
     * Atomic in-DB counter bump so progress is monotonic even if two updates interleave. Runs in
     * its own committed transaction (see {@code RebuildJobStore}) so pollers see live progress.
     */
    @Modifying
    @Query("UPDATE RebuildJobEntity j SET j.processedEvents = j.processedEvents + :delta WHERE j.id = :id")
    void incrementProcessed(@Param("id") UUID id, @Param("delta") long delta);
}
