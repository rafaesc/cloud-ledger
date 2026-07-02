package com.getcloudledger.api.shared.spring;

import com.getcloudledger.api.shared.domain.exception.ConcurrencyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("handleConcurrency | returns 409 version_conflict when the in-memory version check loses")
    void concurrency_maps_to_409() {
        var response = handler.handleConcurrency(new ConcurrencyException("expected 1 but found 2"));

        assertEquals(409, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("version_conflict", response.getBody().get("error"));
    }

    @Test
    @DisplayName("handleDataIntegrity | returns 409 when the loser hits the events version unique constraint")
    void events_version_constraint_maps_to_409() {
        var conflict = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException(
                        "ERROR: duplicate key value violates unique constraint \"uq_events_aggregate_version\""));

        var response = handler.handleDataIntegrity(conflict);

        assertEquals(409, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("version_conflict", response.getBody().get("error"));
    }

    @Test
    @DisplayName("handleDataIntegrity | rethrows (does not mask as 409) for any other constraint violation")
    void other_constraint_is_rethrown() {
        var unrelated = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException(
                        "ERROR: duplicate key value violates unique constraint \"accounts_pkey\""));

        assertThrows(DataIntegrityViolationException.class, () -> handler.handleDataIntegrity(unrelated));
    }
}
