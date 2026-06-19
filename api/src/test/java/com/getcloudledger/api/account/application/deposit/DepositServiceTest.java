package com.getcloudledger.api.account.application.deposit;

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

@DisplayName("DepositService")
class DepositServiceTest {

    @Test
    @DisplayName("deposit | increases account balance and saves when account exists")
    void deposit_increases_balance_and_saves() {
        var handler = mock(AccountEventSourcingHandler.class);
        var service = new DepositService(handler);

        var accountId = UUID.randomUUID();
        var account = openedAccount(accountId);
        when(handler.getById(new AccountId(accountId))).thenReturn(Optional.of(account));

        service.deposit(accountId, new BigDecimal("150.00"));

        var captor = ArgumentCaptor.forClass(Account.class);
        verify(handler).save(captor.capture());
        assertEquals(new BigDecimal("150.00"), captor.getValue().getBalance());
    }

    @Test
    @DisplayName("deposit | throws when account does not exist")
    void deposit_throws_when_account_not_found() {
        var handler = mock(AccountEventSourcingHandler.class);
        var service = new DepositService(handler);

        when(handler.getById(any())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.deposit(UUID.randomUUID(), new BigDecimal("50.00")));
        verify(handler, never()).save(any());
    }

    private Account openedAccount(UUID accountId) {
        var account = Account.open(new AccountId(accountId), UUID.randomUUID(), "USD");
        account.markChangesAsCommitted();
        return account;
    }
}
