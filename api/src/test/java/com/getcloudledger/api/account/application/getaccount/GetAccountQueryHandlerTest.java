package com.getcloudledger.api.account.application.getaccount;

import com.getcloudledger.api.account.domain.exception.AccountNotFoundException;
import com.getcloudledger.api.account.domain.port.out.AccountProjectionPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GetAccountQueryHandler")
class GetAccountQueryHandlerTest {

    private final AccountProjectionPort projectionPort = mock(AccountProjectionPort.class);
    private final GetAccountQueryHandler handler = new GetAccountQueryHandler(projectionPort);

    @Test
    @DisplayName("handle | maps all STATE fields to response when account exists")
    void handle_maps_state_view_to_response() {
        var accountId = UUID.randomUUID();
        var view = new AccountProjectionPort.AccountStateView(
                accountId.toString(), "owner-1", "ACTIVE", "USD", 3L,
                "2026-06-11T14:23:01.123Z", null, null
        );
        when(projectionPort.findAccountState(accountId)).thenReturn(view);

        var response = handler.handle(new GetAccountQuery(accountId));

        assertEquals(accountId.toString(), response.accountId());
        assertEquals("owner-1", response.ownerId());
        assertEquals("ACTIVE", response.status());
        assertEquals("USD", response.currency());
        assertEquals(3L, response.version());
        assertEquals("2026-06-11T14:23:01.123Z", response.openedAt());
        assertNull(response.frozenAt());
        assertNull(response.closedAt());
    }

    @Test
    @DisplayName("handle | includes frozenAt and closedAt when present in STATE view")
    void handle_includes_frozen_at_and_closed_at() {
        var accountId = UUID.randomUUID();
        var view = new AccountProjectionPort.AccountStateView(
                accountId.toString(), "owner-2", "CLOSED", "USD", 5L,
                "2026-06-11T14:23:01.123Z", "2026-06-12T10:00:00.000Z", "2026-06-13T09:00:00.000Z"
        );
        when(projectionPort.findAccountState(accountId)).thenReturn(view);

        var response = handler.handle(new GetAccountQuery(accountId));

        assertEquals("CLOSED", response.status());
        assertEquals("2026-06-12T10:00:00.000Z", response.frozenAt());
        assertEquals("2026-06-13T09:00:00.000Z", response.closedAt());
    }

    @Test
    @DisplayName("handle | propagates AccountNotFoundException when account not found")
    void handle_propagates_not_found() {
        var accountId = UUID.randomUUID();
        when(projectionPort.findAccountState(accountId))
                .thenThrow(new AccountNotFoundException(accountId));

        assertThrows(AccountNotFoundException.class,
                () -> handler.handle(new GetAccountQuery(accountId)));
    }
}
