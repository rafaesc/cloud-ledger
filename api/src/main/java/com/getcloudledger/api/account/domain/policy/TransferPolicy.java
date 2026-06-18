package com.getcloudledger.api.account.domain.policy;

import com.getcloudledger.api.account.domain.model.Account;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransferPolicy {

    private final List<TransferRule> rules;

    public void validate(Account source, Account destination, BigDecimal amount) {
        rules.forEach(rule -> rule.enforce(source, destination, amount));
    }
}
