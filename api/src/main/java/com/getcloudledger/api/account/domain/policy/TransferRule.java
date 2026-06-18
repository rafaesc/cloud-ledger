package com.getcloudledger.api.account.domain.policy;

import com.getcloudledger.api.account.domain.model.Account;

import java.math.BigDecimal;

@FunctionalInterface
public interface TransferRule {
    void enforce(Account source, Account destination, BigDecimal amount);
}
