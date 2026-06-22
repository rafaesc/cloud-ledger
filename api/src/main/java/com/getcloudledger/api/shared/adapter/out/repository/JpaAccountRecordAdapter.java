package com.getcloudledger.api.shared.adapter.out.repository;

import com.getcloudledger.api.account.domain.port.out.AccountRegistryPort;
import com.getcloudledger.api.shared.adapter.out.entities.AccountRecordEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaAccountRecordAdapter implements AccountRegistryPort {

    private final JpaAccountRecordRepository repository;

    @Override
    public void register(String accountId, String ownerId, String currency) {
        repository.save(new AccountRecordEntity(accountId, ownerId, currency));
    }

    @Override
    public boolean isOwner(String accountId, String ownerId) {
        return repository.existsByIdAndOwnerId(accountId, ownerId);
    }
}
