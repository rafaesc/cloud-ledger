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

@DisplayName("SameCurrencyRule")
class SameCurrencyRuleTest {

    private final SameCurrencyRule rule = new SameCurrencyRule();
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    @Test
    @DisplayName("enforce | passes when source and destination share the same currency")
    void enforce_passes_when_currencies_match() {
        var source = account("USD");
        var destination = account("USD");

        assertDoesNotThrow(() -> rule.enforce(source, destination, AMOUNT));
    }

    @Test
    @DisplayName("enforce | throws TransferNotAllowedException when currencies differ")
    void enforce_throws_when_currencies_differ() {
        var source = account("USD");
        var destination = account("EUR");

        assertThrows(TransferNotAllowedException.class,
                () -> rule.enforce(source, destination, AMOUNT));
    }

    @Test
    @DisplayName("enforce | throws TransferNotAllowedException for any mismatched currency pair")
    void enforce_throws_for_any_currency_mismatch() {
        var source = account("GBP");
        var destination = account("BRL");

        assertThrows(TransferNotAllowedException.class,
                () -> rule.enforce(source, destination, AMOUNT));
    }

    private Account account(String currency) {
        return Account.open(AccountId.generate(), UUID.randomUUID().toString(), currency);
    }
}
