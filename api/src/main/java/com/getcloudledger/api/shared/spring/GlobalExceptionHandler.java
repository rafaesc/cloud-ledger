package com.getcloudledger.api.shared.spring;

import com.getcloudledger.api.account.domain.exception.AccountAccessDeniedException;
import com.getcloudledger.api.account.domain.exception.AccountNotFoundException;
import com.getcloudledger.api.account.domain.exception.InsufficientFundsException;
import com.getcloudledger.api.account.domain.exception.TransferNotAllowedException;
import com.getcloudledger.api.admin.application.getrebuildjob.RebuildJobNotFoundException;
import com.getcloudledger.api.shared.domain.exception.ConcurrencyException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Unique constraint fencing optimistic concurrency: events(aggregate_id, version).
    private static final String EVENTS_VERSION_CONSTRAINT = "uq_events_aggregate_version";

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

    @ExceptionHandler(RebuildJobNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleRebuildJobNotFound(RebuildJobNotFoundException e) {
        return ResponseEntity.status(404).body(Map.of("error", "rebuild_job_not_found"));
    }

    // Optimistic-lock conflict detected in-memory (EventStore re-reads the stream and finds a
    // version ahead of the one the aggregate loaded with — the serialized/late-loser case).
    @ExceptionHandler(ConcurrencyException.class)
    public ResponseEntity<Map<String, String>> handleConcurrency(ConcurrencyException e) {
        return ResponseEntity.status(409).body(Map.of("error", "version_conflict"));
    }

    // Two writers that both passed the in-memory check race to INSERT the same
    // (aggregate_id, version): the loser is rejected by the unique constraint and surfaces as a
    // DataIntegrityViolationException. That specific constraint is a concurrency conflict → 409.
    // Any other integrity violation is a genuine 500 — rethrow so default handling applies.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException e) {
        var cause = e.getMostSpecificCause().getMessage();
        if (cause != null && cause.contains(EVENTS_VERSION_CONSTRAINT)) {
            return ResponseEntity.status(409).body(Map.of("error", "version_conflict"));
        }
        throw e;
    }
}
