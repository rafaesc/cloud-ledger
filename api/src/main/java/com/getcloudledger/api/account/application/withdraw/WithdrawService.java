package com.getcloudledger.api.account.application.withdraw;

import com.getcloudledger.api.account.domain.service.AccountEventSourcingHandler;
import com.getcloudledger.api.account.domain.valueobject.AccountId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WithdrawService {

    private final AccountEventSourcingHandler eventSourcingHandler;

    public void withdraw(UUID accountId, BigDecimal amount) {
        var account = eventSourcingHandler.getById(new AccountId(accountId))
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        account.withdraw(amount);
        eventSourcingHandler.save(account);
    }
}
