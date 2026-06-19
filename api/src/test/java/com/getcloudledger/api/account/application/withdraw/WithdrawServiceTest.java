package com.getcloudledger.api.account.application.withdraw;

import com.getcloudledger.api.account.domain.exception.InsufficientFundsException;
import com.getcloudledger.api.account.domain.model.Account;
import com.getcloudledger.api.account.domain.service.AccountEventSourcingHandler;
import com.getcloudledger.api.account.domain.valueobject.AccountId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("WithdrawService")
class WithdrawServiceTest {

    @Test
    @DisplayName("withdraw | decreases account balance and saves when funds are sufficient")
    void withdraw_decreases_balance_and_saves() {
        var handler = mock(AccountEventSourcingHandler.class);
        var service = new WithdrawService(handler);

        var accountId = UUID.randomUUID();
        var account = openedAccountWithBalance(accountId, new BigDecimal("200.00"));
        when(handler.getById(new AccountId(accountId))).thenReturn(Optional.of(account));

        service.withdraw(accountId, new BigDecimal("80.00"));

        var captor = ArgumentCaptor.forClass(Account.class);
        verify(handler).save(captor.capture());
        assertEquals(new BigDecimal("120.00"), captor.getValue().getBalance());
    }

    @Test
    @DisplayName("withdraw | throws InsufficientFundsException when balance is too low")
    void withdraw_throws_when_insufficient_funds() {
        var handler = mock(AccountEventSourcingHandler.class);
        var service = new WithdrawService(handler);

        var accountId = UUID.randomUUID();
        var account = openedAccountWithBalance(accountId, new BigDecimal("10.00"));
        when(handler.getById(new AccountId(accountId))).thenReturn(Optional.of(account));

        assertThrows(InsufficientFundsException.class,
                () -> service.withdraw(accountId, new BigDecimal("50.00")));
        verify(handler, never()).save(any());
    }

    @Test
    @DisplayName("withdraw | throws when account does not exist")
    void withdraw_throws_when_account_not_found() {
        var handler = mock(AccountEventSourcingHandler.class);
        when(handler.getById(any())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> new WithdrawService(handler).withdraw(UUID.randomUUID(), new BigDecimal("10.00")));
    }

    private Account openedAccountWithBalance(UUID accountId, BigDecimal balance) {
        var account = Account.open(new AccountId(accountId), UUID.randomUUID(), "USD");
        account.deposit(balance);
        account.markChangesAsCommitted();
        return account;
    }
}
