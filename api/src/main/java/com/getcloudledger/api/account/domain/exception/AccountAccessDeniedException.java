package com.getcloudledger.api.account.domain.exception;

public class AccountAccessDeniedException extends RuntimeException {
    public AccountAccessDeniedException(String accountId) {
        super("Access to account " + accountId + " is denied");
    }
}
