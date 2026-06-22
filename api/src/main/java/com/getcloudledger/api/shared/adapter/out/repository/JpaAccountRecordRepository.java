package com.getcloudledger.api.shared.adapter.out.repository;

import com.getcloudledger.api.shared.adapter.out.entities.AccountRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaAccountRecordRepository extends JpaRepository<AccountRecordEntity, String> {
    boolean existsByIdAndOwnerId(String id, String ownerId);
}
