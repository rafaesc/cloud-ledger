package com.getcloudledger.api.account.application.freeze;

import com.getcloudledger.api.account.domain.service.AccountEventSourcingHandler;
import com.getcloudledger.api.account.domain.valueobject.AccountId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FreezeAccountService {

    private final AccountEventSourcingHandler eventSourcingHandler;

    public void freeze(UUID accountId) {
        var account = eventSourcingHandler.getById(new AccountId(accountId))
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        account.freeze();
        eventSourcingHandler.save(account);
    }
}
