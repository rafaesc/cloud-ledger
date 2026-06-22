package com.getcloudledger.api.account.application.open;

import com.getcloudledger.api.account.domain.model.Account;
import com.getcloudledger.api.account.domain.port.out.AccountRegistryPort;
import com.getcloudledger.api.account.domain.service.AccountEventSourcingHandler;
import com.getcloudledger.api.account.domain.valueobject.AccountId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountOpenerService {

    private final AccountEventSourcingHandler eventSourcingHandler;
    private final AccountRegistryPort accountRegistryPort;

    @Transactional
    public void open(UUID accountId, String ownerId, String currency) {
        var account = Account.open(new AccountId(accountId), ownerId, currency);
        eventSourcingHandler.save(account);
        accountRegistryPort.register(accountId.toString(), ownerId, currency);
    }
}
