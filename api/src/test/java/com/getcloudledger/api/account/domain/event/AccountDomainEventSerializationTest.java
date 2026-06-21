package com.getcloudledger.api.account.domain.event;

import com.getcloudledger.api.shared.domain.bus.event.DomainEvent;
import com.getcloudledger.api.shared.domain.bus.event.DomainEventJsonSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.math.BigDecimal;
import java.util.UUID;

@DisplayName("Account domain events | serialization contract")
class AccountDomainEventSerializationTest {

    private static final UUID   AGGREGATE_ID = UUID.fromString("aaaa0000-0000-0000-0000-000000000001");
    private static final UUID   USER_ID   = UUID.fromString("bbbb0000-0000-0000-0000-000000000002");
    private static final UUID   EVENT_ID     = UUID.fromString("eeee0000-0000-0000-0000-000000000005");
    private static final UUID   COUNTERPART  = UUID.fromString("cccc0000-0000-0000-0000-000000000003");
    private static final UUID   TRANSFER_ID  = UUID.fromString("dddd0000-0000-0000-0000-000000000004");
    private static final String OCCURRED_ON  = "2024-01-15T10:30:00";
    private static final int    VERSION      = 1;
    private static final String BALANCE      = "500.00";

    private void assertJson(String expected, DomainEvent event) throws Exception {
        JSONAssert.assertEquals(expected, DomainEventJsonSerializer.serialize(event), JSONCompareMode.STRICT);
    }

    // ── AccountOpened ────────────────────────────────────────────────────────

    @Test
    @DisplayName("serialize | AccountOpened full wire format")
    void accountOpened_serialize() throws Exception {
        var event = new AccountOpened(AGGREGATE_ID, USER_ID, EVENT_ID, OCCURRED_ON, VERSION, "USD");

        assertJson("""
                {
                  "data": {
                    "type": "AccountOpened",
                    "event_id": "eeee0000-0000-0000-0000-000000000005",
                    "version": 1,
                    "sequence_number": null,
                    "occurred_on": "2024-01-15T10:30:00",
                    "attributes": {
                      "aggregate_id": "aaaa0000-0000-0000-0000-000000000001",
                      "user_id": "bbbb0000-0000-0000-0000-000000000002",
                      "currency": "USD"
                    }
                  },
                  "meta": {}
                }
                """, event);
    }

    // ── MoneyDeposited ───────────────────────────────────────────────────────

    @Test
    @DisplayName("serialize | MoneyDeposited full wire format — balance_after in meta, not in attributes")
    void moneyDeposited_serialize() throws Exception {
        var event = new MoneyDeposited(AGGREGATE_ID, USER_ID, EVENT_ID, OCCURRED_ON, VERSION,
                new BigDecimal("100.50"));
        event.putDynamicAttribute("balance_after", BALANCE);

        assertJson("""
                {
                  "data": {
                    "type": "MoneyDeposited",
                    "event_id": "eeee0000-0000-0000-0000-000000000005",
                    "version": 1,
                    "sequence_number": null,
                    "occurred_on": "2024-01-15T10:30:00",
                    "attributes": {
                      "aggregate_id": "aaaa0000-0000-0000-0000-000000000001",
                      "user_id": "bbbb0000-0000-0000-0000-000000000002",
                      "amount": "100.50"
                    }
                  },
                  "meta": {
                    "balance_after": "500.00"
                  }
                }
                """, event);
    }

    // ── MoneyWithdrawn ───────────────────────────────────────────────────────

    @Test
    @DisplayName("serialize | MoneyWithdrawn full wire format — balance_after in meta, not in attributes")
    void moneyWithdrawn_serialize() throws Exception {
        var event = new MoneyWithdrawn(AGGREGATE_ID, USER_ID, EVENT_ID, OCCURRED_ON, VERSION,
                new BigDecimal("200.00"));
        event.putDynamicAttribute("balance_after", BALANCE);

        assertJson("""
                {
                  "data": {
                    "type": "MoneyWithdrawn",
                    "event_id": "eeee0000-0000-0000-0000-000000000005",
                    "version": 1,
                    "sequence_number": null,
                    "occurred_on": "2024-01-15T10:30:00",
                    "attributes": {
                      "aggregate_id": "aaaa0000-0000-0000-0000-000000000001",
                      "user_id": "bbbb0000-0000-0000-0000-000000000002",
                      "amount": "200.00"
                    }
                  },
                  "meta": {
                    "balance_after": "500.00"
                  }
                }
                """, event);
    }

    // ── TransferDebited ──────────────────────────────────────────────────────

    @Test
    @DisplayName("serialize | TransferDebited full wire format — balance_after in meta, not in attributes")
    void transferDebited_serialize() throws Exception {
        var event = new TransferDebited(AGGREGATE_ID, USER_ID, EVENT_ID, OCCURRED_ON, VERSION,
                new BigDecimal("300.00"), COUNTERPART, TRANSFER_ID);
        event.putDynamicAttribute("balance_after", BALANCE);

        assertJson("""
                {
                  "data": {
                    "type": "TransferDebited",
                    "event_id": "eeee0000-0000-0000-0000-000000000005",
                    "version": 1,
                    "sequence_number": null,
                    "occurred_on": "2024-01-15T10:30:00",
                    "attributes": {
                      "aggregate_id": "aaaa0000-0000-0000-0000-000000000001",
                      "user_id": "bbbb0000-0000-0000-0000-000000000002",
                      "amount": "300.00",
                      "counterpart_account_id": "cccc0000-0000-0000-0000-000000000003",
                      "transfer_id": "dddd0000-0000-0000-0000-000000000004"
                    }
                  },
                  "meta": {
                    "balance_after": "500.00"
                  }
                }
                """, event);
    }

    // ── TransferCredited ─────────────────────────────────────────────────────

    @Test
    @DisplayName("serialize | TransferCredited full wire format — balance_after in meta, not in attributes")
    void transferCredited_serialize() throws Exception {
        var event = new TransferCredited(AGGREGATE_ID, USER_ID, EVENT_ID, OCCURRED_ON, VERSION,
                new BigDecimal("300.00"), COUNTERPART, TRANSFER_ID);
        event.putDynamicAttribute("balance_after", BALANCE);

        assertJson("""
                {
                  "data": {
                    "type": "TransferCredited",
                    "event_id": "eeee0000-0000-0000-0000-000000000005",
                    "version": 1,
                    "sequence_number": null,
                    "occurred_on": "2024-01-15T10:30:00",
                    "attributes": {
                      "aggregate_id": "aaaa0000-0000-0000-0000-000000000001",
                      "user_id": "bbbb0000-0000-0000-0000-000000000002",
                      "amount": "300.00",
                      "counterpart_account_id": "cccc0000-0000-0000-0000-000000000003",
                      "transfer_id": "dddd0000-0000-0000-0000-000000000004"
                    }
                  },
                  "meta": {
                    "balance_after": "500.00"
                  }
                }
                """, event);
    }

    // ── TransferFailed ───────────────────────────────────────────────────────

    @Test
    @DisplayName("serialize | TransferFailed full wire format — balance_after in meta, not in attributes")
    void transferFailed_serialize() throws Exception {
        var event = new TransferFailed(AGGREGATE_ID, USER_ID, EVENT_ID, OCCURRED_ON, VERSION,
                new BigDecimal("150.00"), COUNTERPART, TRANSFER_ID, "INSUFFICIENT_FUNDS");
        event.putDynamicAttribute("balance_after", BALANCE);

        assertJson("""
                {
                  "data": {
                    "type": "TransferFailed",
                    "event_id": "eeee0000-0000-0000-0000-000000000005",
                    "version": 1,
                    "sequence_number": null,
                    "occurred_on": "2024-01-15T10:30:00",
                    "attributes": {
                      "aggregate_id": "aaaa0000-0000-0000-0000-000000000001",
                      "user_id": "bbbb0000-0000-0000-0000-000000000002",
                      "amount": "150.00",
                      "counterpart_account_id": "cccc0000-0000-0000-0000-000000000003",
                      "transfer_id": "dddd0000-0000-0000-0000-000000000004",
                      "reason": "INSUFFICIENT_FUNDS"
                    }
                  },
                  "meta": {
                    "balance_after": "500.00"
                  }
                }
                """, event);
    }

    // ── AccountFrozen ────────────────────────────────────────────────────────

    @Test
    @DisplayName("serialize | AccountFrozen full wire format")
    void accountFrozen_serialize() throws Exception {
        var event = new AccountFrozen(AGGREGATE_ID, USER_ID, EVENT_ID, OCCURRED_ON, VERSION);

        assertJson("""
                {
                  "data": {
                    "type": "AccountFrozen",
                    "event_id": "eeee0000-0000-0000-0000-000000000005",
                    "version": 1,
                    "sequence_number": null,
                    "occurred_on": "2024-01-15T10:30:00",
                    "attributes": {
                      "aggregate_id": "aaaa0000-0000-0000-0000-000000000001",
                      "user_id": "bbbb0000-0000-0000-0000-000000000002"
                    }
                  },
                  "meta": {}
                }
                """, event);
    }

    // ── AccountClosed ────────────────────────────────────────────────────────

    @Test
    @DisplayName("serialize | AccountClosed full wire format")
    void accountClosed_serialize() throws Exception {
        var event = new AccountClosed(AGGREGATE_ID, USER_ID, EVENT_ID, OCCURRED_ON, VERSION);

        assertJson("""
                {
                  "data": {
                    "type": "AccountClosed",
                    "event_id": "eeee0000-0000-0000-0000-000000000005",
                    "version": 1,
                    "sequence_number": null,
                    "occurred_on": "2024-01-15T10:30:00",
                    "attributes": {
                      "aggregate_id": "aaaa0000-0000-0000-0000-000000000001",
                      "user_id": "bbbb0000-0000-0000-0000-000000000002"
                    }
                  },
                  "meta": {}
                }
                """, event);
    }
}
