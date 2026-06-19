package com.getcloudledger.api.account.application.open;

import com.getcloudledger.api.account.domain.model.Account;
import com.getcloudledger.api.account.domain.service.AccountEventSourcingHandler;
import com.getcloudledger.api.account.domain.valueobject.AccountId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountOpenerService {

    private final AccountEventSourcingHandler eventSourcingHandler;

    public void open(UUID accountId, UUID userId, String currency) {
        var account = Account.open(new AccountId(accountId), userId, currency);
        eventSourcingHandler.save(account);
    }
}
