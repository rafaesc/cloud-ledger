package com.getcloudledger.api.shared.adapter.out.repository;

import com.getcloudledger.api.shared.adapter.out.entities.OutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JpaOutboxRepository extends JpaRepository<OutboxEntity, UUID> {

    List<OutboxEntity> findAllByPublishedAtIsNull();
}
