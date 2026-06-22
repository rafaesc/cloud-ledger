package com.getcloudledger.api.shared.spring;

import com.getcloudledger.api.account.domain.exception.AccountAccessDeniedException;
import com.getcloudledger.api.account.domain.exception.AccountNotFoundException;
import com.getcloudledger.api.account.domain.exception.InsufficientFundsException;
import com.getcloudledger.api.account.domain.exception.TransferNotAllowedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccountAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> handleAccountAccessDenied(AccountAccessDeniedException e) {
        return Map.of("error", "account_access_denied");
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<Map<String, String>> handleInsufficientFunds(InsufficientFundsException e) {
        return ResponseEntity.status(422).body(Map.of("error", "insufficient_funds"));
    }

    @ExceptionHandler(TransferNotAllowedException.class)
    public ResponseEntity<Map<String, String>> handleTransferNotAllowed(TransferNotAllowedException e) {
        return ResponseEntity.status(422).body(Map.of("error", "transfer_not_allowed"));
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleAccountNotFound(AccountNotFoundException e) {
        return ResponseEntity.status(404).body(Map.of("error", "account_not_found"));
    }
}
