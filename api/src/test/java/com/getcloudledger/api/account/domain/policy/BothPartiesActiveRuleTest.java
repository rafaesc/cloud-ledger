package com.getcloudledger.api.account.domain.policy;

import com.getcloudledger.api.account.domain.exception.TransferNotAllowedException;
import com.getcloudledger.api.account.domain.model.Account;
import com.getcloudledger.api.account.domain.valueobject.AccountId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("BothPartiesActiveRule")
class BothPartiesActiveRuleTest {

    private final BothPartiesActiveRule rule = new BothPartiesActiveRule();
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    @Test
    @DisplayName("enforce | passes when both source and destination are ACTIVE")
    void enforce_passes_when_both_accounts_are_active() {
        var source = activeAccount();
        var destination = activeAccount();

        assertDoesNotThrow(() -> rule.enforce(source, destination, AMOUNT));
    }

    @Test
    @DisplayName("enforce | throws TransferNotAllowedException when source is FROZEN")
    void enforce_throws_when_source_is_frozen() {
        var source = activeAccount();
        source.freeze();
        var destination = activeAccount();

        assertThrows(TransferNotAllowedException.class,
                () -> rule.enforce(source, destination, AMOUNT));
    }

    @Test
    @DisplayName("enforce | throws TransferNotAllowedException when source is CLOSED")
    void enforce_throws_when_source_is_closed() {
        var source = activeAccount();
        source.close();
        var destination = activeAccount();

        assertThrows(TransferNotAllowedException.class,
                () -> rule.enforce(source, destination, AMOUNT));
    }

    @Test
    @DisplayName("enforce | throws TransferNotAllowedException when destination is FROZEN")
    void enforce_throws_when_destination_is_frozen() {
        var source = activeAccount();
        var destination = activeAccount();
        destination.freeze();

        assertThrows(TransferNotAllowedException.class,
                () -> rule.enforce(source, destination, AMOUNT));
    }

    @Test
    @DisplayName("enforce | throws TransferNotAllowedException when destination is CLOSED")
    void enforce_throws_when_destination_is_closed() {
        var source = activeAccount();
        var destination = activeAccount();
        destination.close();

        assertThrows(TransferNotAllowedException.class,
                () -> rule.enforce(source, destination, AMOUNT));
    }

    private Account activeAccount() {
        return Account.open(AccountId.generate(), UUID.randomUUID(), "USD");
    }
}
