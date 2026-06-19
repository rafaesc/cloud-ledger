package com.getcloudledger.api.account.application.deposit;

import com.getcloudledger.api.account.domain.service.AccountEventSourcingHandler;
import com.getcloudledger.api.account.domain.valueobject.AccountId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DepositService {

    private final AccountEventSourcingHandler eventSourcingHandler;

    public void deposit(UUID accountId, BigDecimal amount) {
        var account = eventSourcingHandler.getById(new AccountId(accountId))
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        account.deposit(amount);
        eventSourcingHandler.save(account);
    }
}
