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

@DisplayName("NoSelfTransferRule")
class NoSelfTransferRuleTest {

    private final NoSelfTransferRule rule = new NoSelfTransferRule();

    @Test
    @DisplayName("enforce | throws TransferNotAllowedException when source and destination are the same account")
    void enforce_throws_when_source_and_destination_are_same_account() {
        var account = Account.open(AccountId.generate(), UUID.randomUUID(), "USD");

        assertThrows(TransferNotAllowedException.class,
                () -> rule.enforce(account, account, new BigDecimal("100.00")));
    }

    @Test
    @DisplayName("enforce | passes when source and destination are different accounts")
    void enforce_passes_when_source_and_destination_are_different_accounts() {
        var source = Account.open(AccountId.generate(), UUID.randomUUID(), "USD");
        var destination = Account.open(AccountId.generate(), UUID.randomUUID(), "USD");

        assertDoesNotThrow(() -> rule.enforce(source, destination, new BigDecimal("100.00")));
    }
}
