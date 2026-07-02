package com.getcloudledger.api.shared.adapter.out.repository;

import com.getcloudledger.api.shared.adapter.out.entities.DomainEventEntity;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JpaDomainEventRepository extends JpaRepository<DomainEventEntity, UUID> {

    List<DomainEventEntity> findAllByAggregateId(UUID aggregateId, Sort sort);

    long countByAggregateId(UUID aggregateId);

    /**
     * Keyset page of one aggregate's events by ascending version — used by the projection rebuild
     * so a single aggregate's stream is never fully materialised in memory. {@code version} is the
     * exclusive lower bound already processed ({@code -1} for the first page).
     */
    List<DomainEventEntity> findByAggregateIdAndVersionGreaterThanOrderByVersionAsc(
            UUID aggregateId, Integer version, Limit limit);

    /**
     * Keyset page of distinct aggregate ids for a full rebuild. {@code after} is the exclusive lower
     * bound (the all-zero UUID for the first page); ids are ordered so paging is stable.
     */
    @Query("SELECT DISTINCT e.aggregateId FROM DomainEventEntity e "
            + "WHERE e.aggregateId > :after ORDER BY e.aggregateId")
    List<UUID> findDistinctAggregateIdsAfter(@Param("after") UUID after, Limit limit);
}
