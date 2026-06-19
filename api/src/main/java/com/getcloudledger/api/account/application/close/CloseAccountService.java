package com.getcloudledger.api.account.application.close;

import com.getcloudledger.api.account.domain.service.AccountEventSourcingHandler;
import com.getcloudledger.api.account.domain.valueobject.AccountId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CloseAccountService {

    private final AccountEventSourcingHandler eventSourcingHandler;

    public void close(UUID accountId) {
        var account = eventSourcingHandler.getById(new AccountId(accountId))
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        account.close();
        eventSourcingHandler.save(account);
    }
}
