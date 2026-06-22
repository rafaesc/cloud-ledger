package com.getcloudledger.api.account.application.gettransactions;

import com.getcloudledger.api.account.domain.port.out.AccountProjectionPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GetTransactionsQueryHandler")
class GetTransactionsQueryHandlerTest {

    private final AccountProjectionPort projectionPort = mock(AccountProjectionPort.class);
    private final GetTransactionsQueryHandler handler = new GetTransactionsQueryHandler(projectionPort);

    @Test
    @DisplayName("handle | passes null startSK to port when cursor is absent")
    void handle_passes_null_start_sk_when_no_cursor() {
        var accountId = UUID.randomUUID();
        when(projectionPort.findTransactions(eq(accountId), anyInt(), isNull(), anyBoolean()))
                .thenReturn(new AccountProjectionPort.TransactionPage(List.of(), null));

        handler.handle(new GetTransactionsQuery(accountId, 50, null, false));

        verify(projectionPort).findTransactions(accountId, 50, null, false);
    }

    @Test
    @DisplayName("handle | decodes base64 cursor to raw SK before passing to port")
    void handle_decodes_cursor_to_raw_sk() {
        var accountId = UUID.randomUUID();
        String rawSK = "TXNS#00000000000000004217";
        String cursor = Base64.getEncoder().encodeToString(rawSK.getBytes(StandardCharsets.UTF_8));

        when(projectionPort.findTransactions(eq(accountId), anyInt(), any(), anyBoolean()))
                .thenReturn(new AccountProjectionPort.TransactionPage(List.of(), null));

        handler.handle(new GetTransactionsQuery(accountId, 50, cursor, false));

        var skCaptor = ArgumentCaptor.forClass(String.class);
        verify(projectionPort).findTransactions(eq(accountId), eq(50), skCaptor.capture(), eq(false));
        assertEquals(rawSK, skCaptor.getValue());
    }

    @Test
    @DisplayName("handle | maps transaction views to response items and passes through nextCursor")
    void handle_maps_transaction_views_and_next_cursor() {
        var accountId = UUID.randomUUID();
        var txView = new AccountProjectionPort.TransactionView(
                4217L, "MoneyDeposited", "CREDIT", 500_00L,
                null, null, "evt-001", "2026-06-11T14:24:10.880Z"
        );
        String nextCursor = Base64.getEncoder().encodeToString("TXNS#00000000000000004217".getBytes(StandardCharsets.UTF_8));
        when(projectionPort.findTransactions(any(), anyInt(), any(), anyBoolean()))
                .thenReturn(new AccountProjectionPort.TransactionPage(List.of(txView), nextCursor));

        var response = handler.handle(new GetTransactionsQuery(accountId, 2, null, false));

        assertEquals(accountId.toString(), response.accountId());
        assertEquals(1, response.items().size());
        var item = response.items().getFirst();
        assertEquals(4217L, item.sequenceNumber());
        assertEquals("MoneyDeposited", item.eventType());
        assertEquals("CREDIT", item.direction());
        assertEquals(500_00L, item.amountCents());
        assertNull(item.counterpartAccountId());
        assertNull(item.transferId());
        assertEquals("evt-001", item.eventId());
        assertEquals("2026-06-11T14:24:10.880Z", item.occurredAt());
        assertEquals(nextCursor, response.nextCursor());
    }

    @Test
    @DisplayName("handle | returns null nextCursor when last page")
    void handle_returns_null_next_cursor_on_last_page() {
        var accountId = UUID.randomUUID();
        when(projectionPort.findTransactions(any(), anyInt(), any(), anyBoolean()))
                .thenReturn(new AccountProjectionPort.TransactionPage(List.of(), null));

        var response = handler.handle(new GetTransactionsQuery(accountId, 50, null, false));

        assertNull(response.nextCursor());
    }

    @Test
    @DisplayName("handle | passes ascending flag through to port")
    void handle_passes_ascending_flag_to_port() {
        var accountId = UUID.randomUUID();
        when(projectionPort.findTransactions(any(), anyInt(), any(), anyBoolean()))
                .thenReturn(new AccountProjectionPort.TransactionPage(List.of(), null));

        handler.handle(new GetTransactionsQuery(accountId, 10, null, true));

        verify(projectionPort).findTransactions(accountId, 10, null, true);
    }
}
