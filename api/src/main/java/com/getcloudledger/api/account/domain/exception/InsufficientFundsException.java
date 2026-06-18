package com.getcloudledger.api.account.domain.exception;

import com.getcloudledger.api.account.domain.valueobject.AccountId;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(AccountId accountId, BigDecimal balance, BigDecimal requested) {
        super("Account %s has insufficient funds: balance=%s, requested=%s"
                .formatted(accountId, balance.toPlainString(), requested.toPlainString()));
    }
}
