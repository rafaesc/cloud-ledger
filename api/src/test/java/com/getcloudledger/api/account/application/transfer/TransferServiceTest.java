package com.getcloudledger.api.account.application.transfer;

import com.getcloudledger.api.account.domain.exception.TransferNotAllowedException;
import com.getcloudledger.api.account.domain.model.Account;
import com.getcloudledger.api.account.domain.policy.BothPartiesActiveRule;
import com.getcloudledger.api.account.domain.policy.NoSelfTransferRule;
import com.getcloudledger.api.account.domain.policy.SameCurrencyRule;
import com.getcloudledger.api.account.domain.policy.TransferPolicy;
import com.getcloudledger.api.account.domain.service.AccountEventSourcingHandler;
import com.getcloudledger.api.account.domain.valueobject.AccountId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("TransferService")
class TransferServiceTest {

    @Test
    @DisplayName("transfer | debits source and credits destination and saves both accounts")
    void transfer_debits_source_and_credits_destination() {
        var handler = mock(AccountEventSourcingHandler.class);
        var service = new TransferService(handler, realPolicy());

        var sourceId = UUID.randomUUID();
        var destinationId = UUID.randomUUID();
        var transferId = UUID.randomUUID();

        var source = openedAccountWithBalance(sourceId, new BigDecimal("500.00"), "USD");
        var destination = openedAccount(destinationId, "USD");
        when(handler.getById(new AccountId(sourceId))).thenReturn(Optional.of(source));
        when(handler.getById(new AccountId(destinationId))).thenReturn(Optional.of(destination));

        service.transfer(sourceId, destinationId, new BigDecimal("200.00"), transferId);

        var captor = ArgumentCaptor.forClass(Account.class);
        verify(handler, times(2)).save(captor.capture());
        var saved = captor.getAllValues();
        assertEquals(new BigDecimal("300.00"), saved.get(0).getBalance());
        assertEquals(new BigDecimal("200.00"), saved.get(1).getBalance());
    }

    @Test
    @DisplayName("transfer | throws TransferNotAllowedException when source and destination are the same")
    void transfer_throws_on_self_transfer() {
        var handler = mock(AccountEventSourcingHandler.class);
        var service = new TransferService(handler, realPolicy());

        var accountId = UUID.randomUUID();
        var account = openedAccountWithBalance(accountId, new BigDecimal("100.00"), "USD");
        when(handler.getById(new AccountId(accountId))).thenReturn(Optional.of(account));

        assertThrows(TransferNotAllowedException.class,
                () -> service.transfer(accountId, accountId, new BigDecimal("50.00"), UUID.randomUUID()));
        verify(handler, never()).save(any());
    }

    @Test
    @DisplayName("transfer | throws TransferNotAllowedException on currency mismatch")
    void transfer_throws_on_currency_mismatch() {
        var handler = mock(AccountEventSourcingHandler.class);
        var service = new TransferService(handler, realPolicy());

        var sourceId = UUID.randomUUID();
        var destinationId = UUID.randomUUID();
        var source = openedAccountWithBalance(sourceId, new BigDecimal("100.00"), "USD");
        var destination = openedAccount(destinationId, "EUR");
        when(handler.getById(new AccountId(sourceId))).thenReturn(Optional.of(source));
        when(handler.getById(new AccountId(destinationId))).thenReturn(Optional.of(destination));

        assertThrows(TransferNotAllowedException.class,
                () -> service.transfer(sourceId, destinationId, new BigDecimal("50.00"), UUID.randomUUID()));
        verify(handler, never()).save(any());
    }

    @Test
    @DisplayName("transfer | throws when source account does not exist")
    void transfer_throws_when_source_not_found() {
        var handler = mock(AccountEventSourcingHandler.class);
        when(handler.getById(any())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> new TransferService(handler, realPolicy())
                        .transfer(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.00"), UUID.randomUUID()));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private TransferPolicy realPolicy() {
        return new TransferPolicy(List.of(
                new NoSelfTransferRule(),
                new BothPartiesActiveRule(),
                new SameCurrencyRule()));
    }

    private Account openedAccount(UUID accountId, String currency) {
        var account = Account.open(new AccountId(accountId), UUID.randomUUID().toString(), currency);
        account.markChangesAsCommitted();
        return account;
    }

    private Account openedAccountWithBalance(UUID accountId, BigDecimal balance, String currency) {
        var account = Account.open(new AccountId(accountId), UUID.randomUUID().toString(), currency);
        account.deposit(balance);
        account.markChangesAsCommitted();
        return account;
    }
}
