package com.getcloudledger.api.shared.spring;

import com.getcloudledger.api.account.domain.exception.AccountAccessDeniedException;
import org.springframework.http.HttpStatus;
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
}
