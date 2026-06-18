package com.getcloudledger.api.account.domain.model;

import com.getcloudledger.api.account.domain.event.TransferFailed;
import com.getcloudledger.api.account.domain.exception.InsufficientFundsException;
import com.getcloudledger.api.account.domain.valueobject.AccountId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Account aggregate")
class AccountTest {

    // ── open ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("open | creates ACTIVE account with zero balance and one uncommitted event")
    void open_initialises_active_account_with_zero_balance() {
        var accountId = AccountId.generate();
        var ownerId = UUID.randomUUID();

        var account = Account.open(accountId, ownerId, "USD");

        assertEquals(accountId, account.getId());
        assertEquals(ownerId, account.getUserId());
        assertEquals("USD", account.getCurrency());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertEquals(BigDecimal.ZERO, account.getBalance());
        assertEquals(1, account.pullUncommittedChanges().size());
    }

    // ── deposit ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deposit | increases balance by the deposited amount")
    void deposit_increases_balance() {
        var account = activeAccount();

        account.deposit(new BigDecimal("100.00"));

        assertEquals(new BigDecimal("100.00"), account.getBalance());
    }

    @Test
    @DisplayName("deposit | accumulates correctly across multiple calls")
    void deposit_accumulates_across_multiple_calls() {
        var account = activeAccount();

        account.deposit(new BigDecimal("100.00"));
        account.deposit(new BigDecimal("50.00"));

        assertEquals(new BigDecimal("150.00"), account.getBalance());
    }

    @Test
    @DisplayName("deposit | throws IllegalArgumentException when amount is zero")
    void deposit_throws_when_amount_is_zero() {
        assertThrows(IllegalArgumentException.class,
                () -> activeAccount().deposit(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("deposit | throws IllegalArgumentException when amount is negative")
    void deposit_throws_when_amount_is_negative() {
        assertThrows(IllegalArgumentException.class,
                () -> activeAccount().deposit(new BigDecimal("-1.00")));
    }

    @Test
    @DisplayName("deposit | throws IllegalStateException when account is FROZEN")
    void deposit_throws_when_account_is_frozen() {
        var account = activeAccount();
        account.freeze();

        assertThrows(IllegalStateException.class, () -> account.deposit(new BigDecimal("100.00")));
    }

    @Test
    @DisplayName("deposit | throws IllegalStateException when account is CLOSED")
    void deposit_throws_when_account_is_closed() {
        var account = activeAccount();
        account.close();

        assertThrows(IllegalStateException.class, () -> account.deposit(new BigDecimal("100.00")));
    }

    // ── withdraw ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("withdraw | decreases balance by the withdrawn amount")
    void withdraw_decreases_balance() {
        var account = fundedAccount("200.00");

        account.withdraw(new BigDecimal("75.00"));

        assertEquals(new BigDecimal("125.00"), account.getBalance());
    }

    @Test
    @DisplayName("withdraw | succeeds when amount equals exact available balance")
    void withdraw_exact_balance_succeeds() {
        var account = fundedAccount("100.00");

        assertDoesNotThrow(() -> account.withdraw(new BigDecimal("100.00")));
        assertEquals(0, account.getBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("withdraw | throws InsufficientFundsException when balance is insufficient")
    void withdraw_throws_when_insufficient_funds() {
        var account = fundedAccount("50.00");

        assertThrows(InsufficientFundsException.class, () -> account.withdraw(new BigDecimal("100.00")));
    }

    @Test
    @DisplayName("withdraw | throws IllegalStateException when account is FROZEN")
    void withdraw_throws_when_account_is_frozen() {
        var account = fundedAccount("200.00");
        account.freeze();

        assertThrows(IllegalStateException.class, () -> account.withdraw(new BigDecimal("50.00")));
    }

    @Test
    @DisplayName("withdraw | throws IllegalArgumentException when amount is zero")
    void withdraw_throws_when_amount_is_zero() {
        assertThrows(IllegalArgumentException.class,
                () -> fundedAccount("100.00").withdraw(BigDecimal.ZERO));
    }

    // ── debitTransfer ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("debitTransfer | decreases balance by the transferred amount")
    void debitTransfer_decreases_balance() {
        var account = fundedAccount("500.00");

        account.debitTransfer(new BigDecimal("200.00"), UUID.randomUUID(), UUID.randomUUID());

        assertEquals(new BigDecimal("300.00"), account.getBalance());
    }

    @Test
    @DisplayName("debitTransfer | throws InsufficientFundsException when balance is insufficient")
    void debitTransfer_throws_when_insufficient_funds() {
        var account = fundedAccount("100.00");

        assertThrows(InsufficientFundsException.class,
                () -> account.debitTransfer(new BigDecimal("200.00"), UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    @DisplayName("debitTransfer | throws IllegalStateException when account is FROZEN")
    void debitTransfer_throws_when_account_is_frozen() {
        var account = fundedAccount("500.00");
        account.freeze();

        assertThrows(IllegalStateException.class,
                () -> account.debitTransfer(new BigDecimal("100.00"), UUID.randomUUID(), UUID.randomUUID()));
    }

    // ── creditTransfer ────────────────────────────────────────────────────────

    @Test
    @DisplayName("creditTransfer | increases balance by the credited amount")
    void creditTransfer_increases_balance() {
        var account = activeAccount();

        account.creditTransfer(new BigDecimal("150.00"), UUID.randomUUID(), UUID.randomUUID());

        assertEquals(new BigDecimal("150.00"), account.getBalance());
    }

    @Test
    @DisplayName("creditTransfer | throws IllegalStateException when account is FROZEN")
    void creditTransfer_throws_when_account_is_frozen() {
        var account = activeAccount();
        account.freeze();

        assertThrows(IllegalStateException.class,
                () -> account.creditTransfer(new BigDecimal("100.00"), UUID.randomUUID(), UUID.randomUUID()));
    }

    // ── transfer symmetry ─────────────────────────────────────────────────────

    @Test
    @DisplayName("transfer | debit and credit amounts are symmetric for the same transferId")
    void debit_and_credit_are_symmetric_for_same_transfer() {
        var transferId = UUID.randomUUID();
        var amount = new BigDecimal("300.00");
        var sender = fundedAccount("500.00");
        var receiver = activeAccount();

        sender.debitTransfer(amount, receiver.getId().getValue(), transferId);
        receiver.creditTransfer(amount, sender.getId().getValue(), transferId);

        assertEquals(new BigDecimal("200.00"), sender.getBalance());
        assertEquals(new BigDecimal("300.00"), receiver.getBalance());
    }

    // ── failTransfer ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("failTransfer | restores balance by adding back the failed transfer amount")
    void failTransfer_restores_balance() {
        var account = fundedAccount("500.00");
        var transferId = UUID.randomUUID();
        account.debitTransfer(new BigDecimal("200.00"), UUID.randomUUID(), transferId);

        account.failTransfer(new BigDecimal("200.00"), UUID.randomUUID(), transferId, "credit side rejected");

        assertEquals(new BigDecimal("500.00"), account.getBalance());
    }

    @Test
    @DisplayName("failTransfer | records exactly one TransferFailed event")
    void failTransfer_records_one_event() {
        var account = fundedAccount("300.00");
        account.debitTransfer(new BigDecimal("100.00"), UUID.randomUUID(), UUID.randomUUID());
        account.drainDomainEvents();

        account.failTransfer(new BigDecimal("100.00"), UUID.randomUUID(), UUID.randomUUID(), "currency mismatch");

        var changes = account.pullUncommittedChanges();
        assertEquals(1, changes.size());
        assertInstanceOf(TransferFailed.class, changes.getFirst());
    }

    @Test
    @DisplayName("failTransfer | works on a FROZEN account to allow compensation")
    void failTransfer_succeeds_on_frozen_account() {
        var account = fundedAccount("300.00");
        account.debitTransfer(new BigDecimal("100.00"), UUID.randomUUID(), UUID.randomUUID());
        account.freeze();

        assertDoesNotThrow(() ->
                account.failTransfer(new BigDecimal("100.00"), UUID.randomUUID(), UUID.randomUUID(), "receiver frozen"));
    }

    @Test
    @DisplayName("failTransfer | throws IllegalArgumentException when amount is zero")
    void failTransfer_throws_when_amount_is_zero() {
        assertThrows(IllegalArgumentException.class,
                () -> activeAccount().failTransfer(BigDecimal.ZERO, UUID.randomUUID(), UUID.randomUUID(), "reason"));
    }

    // ── freeze ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("freeze | transitions account status from ACTIVE to FROZEN")
    void freeze_sets_status_to_frozen() {
        var account = activeAccount();

        account.freeze();

        assertEquals(AccountStatus.FROZEN, account.getStatus());
    }

    @Test
    @DisplayName("freeze | throws IllegalStateException when account is already FROZEN")
    void freeze_throws_when_already_frozen() {
        var account = activeAccount();
        account.freeze();

        assertThrows(IllegalStateException.class, account::freeze);
    }

    @Test
    @DisplayName("freeze | throws IllegalStateException when account is CLOSED")
    void freeze_throws_when_account_is_closed() {
        var account = activeAccount();
        account.close();

        assertThrows(IllegalStateException.class, account::freeze);
    }

    // ── close ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("close | transitions account status to CLOSED from ACTIVE")
    void close_sets_status_to_closed() {
        var account = activeAccount();

        account.close();

        assertEquals(AccountStatus.CLOSED, account.getStatus());
    }

    @Test
    @DisplayName("close | throws IllegalStateException when account is already CLOSED")
    void close_throws_when_already_closed() {
        var account = activeAccount();
        account.close();

        assertThrows(IllegalStateException.class, account::close);
    }

    @Test
    @DisplayName("close | succeeds when transitioning from FROZEN to CLOSED")
    void close_succeeds_from_frozen_status() {
        var account = activeAccount();
        account.freeze();

        assertDoesNotThrow(account::close);
        assertEquals(AccountStatus.CLOSED, account.getStatus());
    }

    // ── event sourcing reconstitution ─────────────────────────────────────────

    @Test
    @DisplayName("replayEvents | reconstitutes complete account state from event stream")
    void replayEvents_reconstitutes_complete_account_state() {
        var accountId = AccountId.generate();
        var ownerId = UUID.randomUUID();
        var original = Account.open(accountId, ownerId, "EUR");
        original.deposit(new BigDecimal("1000.00"));
        original.withdraw(new BigDecimal("200.00"));
        original.creditTransfer(new BigDecimal("500.00"), UUID.randomUUID(), UUID.randomUUID());

        var reconstituted = new Account();
        reconstituted.replayEvents(original.pullUncommittedChanges());

        assertEquals(accountId, reconstituted.getId());
        assertEquals(ownerId, reconstituted.getUserId());
        assertEquals("EUR", reconstituted.getCurrency());
        assertEquals(AccountStatus.ACTIVE, reconstituted.getStatus());
        assertEquals(new BigDecimal("1300.00"), reconstituted.getBalance());
    }

    @Test
    @DisplayName("replayEvents | reconstitutes FROZEN status from event stream")
    void replayEvents_reconstitutes_frozen_status() {
        var original = fundedAccount("100.00");
        original.freeze();

        var reconstituted = new Account();
        reconstituted.replayEvents(original.pullUncommittedChanges());

        assertEquals(AccountStatus.FROZEN, reconstituted.getStatus());
        assertEquals(new BigDecimal("100.00"), reconstituted.getBalance());
    }

    @Test
    @DisplayName("replayEvents | reconstitutes CLOSED status from event stream")
    void replayEvents_reconstitutes_closed_status() {
        var original = activeAccount();
        original.close();

        var reconstituted = new Account();
        reconstituted.replayEvents(original.pullUncommittedChanges());

        assertEquals(AccountStatus.CLOSED, reconstituted.getStatus());
    }

    @Test
    @DisplayName("replayEvents | produces no uncommitted changes on reconstituted aggregate")
    void replayEvents_produces_no_uncommitted_changes() {
        var original = fundedAccount("200.00");

        var reconstituted = new Account();
        reconstituted.replayEvents(original.pullUncommittedChanges());

        assertTrue(reconstituted.pullUncommittedChanges().isEmpty());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Account activeAccount() {
        return Account.open(AccountId.generate(), UUID.randomUUID(), "USD");
    }

    private Account fundedAccount(String amount) {
        var account = activeAccount();
        account.deposit(new BigDecimal(amount));
        return account;
    }
}
