package com.getcloudledger.api.account.domain.exception;

public class TransferNotAllowedException extends RuntimeException {

    public TransferNotAllowedException(String message) {
        super(message);
    }
}
