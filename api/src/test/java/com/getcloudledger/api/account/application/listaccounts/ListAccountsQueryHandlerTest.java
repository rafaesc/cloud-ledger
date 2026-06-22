package com.getcloudledger.api.account.application.listaccounts;

import com.getcloudledger.api.account.domain.port.out.AccountProjectionPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ListAccountsQueryHandler")
class ListAccountsQueryHandlerTest {

    private final AccountProjectionPort projectionPort = mock(AccountProjectionPort.class);
    private final ListAccountsQueryHandler handler = new ListAccountsQueryHandler(projectionPort);

    @Test
    @DisplayName("handle | maps AccountSummaryView list to response with correct ownerId")
    void handle_maps_summary_views_to_response() {
        var ownerId = "cognito-client-abc123";
        var summary1 = new AccountProjectionPort.AccountSummaryView(
                "acc-uuid-1", "ACTIVE", "USD", "2026-06-11T14:23:01.123Z"
        );
        var summary2 = new AccountProjectionPort.AccountSummaryView(
                "acc-uuid-2", "FROZEN", "USD", "2026-06-12T09:00:00.000Z"
        );
        when(projectionPort.listByOwner(ownerId)).thenReturn(List.of(summary1, summary2));

        var response = handler.handle(new ListAccountsQuery(ownerId));

        assertEquals(ownerId, response.ownerId());
        assertEquals(2, response.items().size());
        assertEquals("acc-uuid-1", response.items().get(0).accountId());
        assertEquals("ACTIVE", response.items().get(0).status());
        assertEquals("USD", response.items().get(0).currency());
        assertEquals("2026-06-11T14:23:01.123Z", response.items().get(0).openedAt());
        assertEquals("FROZEN", response.items().get(1).status());
    }

    @Test
    @DisplayName("handle | returns empty items list when owner has no accounts")
    void handle_returns_empty_list_when_no_accounts() {
        var ownerId = "new-client-with-no-accounts";
        when(projectionPort.listByOwner(ownerId)).thenReturn(List.of());

        var response = handler.handle(new ListAccountsQuery(ownerId));

        assertEquals(ownerId, response.ownerId());
        assertTrue(response.items().isEmpty());
    }

    @Test
    @DisplayName("handle | delegates to projectionPort with the query ownerId")
    void handle_delegates_to_projection_port() {
        var ownerId = "some-owner";
        when(projectionPort.listByOwner(ownerId)).thenReturn(List.of());

        handler.handle(new ListAccountsQuery(ownerId));

        verify(projectionPort).listByOwner(ownerId);
    }
}
