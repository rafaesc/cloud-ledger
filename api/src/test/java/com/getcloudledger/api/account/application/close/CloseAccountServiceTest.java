package com.getcloudledger.api.account.application.close;

import com.getcloudledger.api.account.domain.model.Account;
import com.getcloudledger.api.account.domain.model.AccountStatus;
import com.getcloudledger.api.account.domain.service.AccountEventSourcingHandler;
import com.getcloudledger.api.account.domain.valueobject.AccountId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CloseAccountService")
class CloseAccountServiceTest {

    @Test
    @DisplayName("close | transitions account to CLOSED status and saves")
    void close_sets_status_to_closed_and_saves() {
        var handler = mock(AccountEventSourcingHandler.class);
        var service = new CloseAccountService(handler);

        var accountId = UUID.randomUUID();
        var account = openedAccount(accountId);
        when(handler.getById(new AccountId(accountId))).thenReturn(Optional.of(account));

        service.close(accountId);

        var captor = ArgumentCaptor.forClass(Account.class);
        verify(handler).save(captor.capture());
        assertEquals(AccountStatus.CLOSED, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("close | throws when account does not exist")
    void close_throws_when_account_not_found() {
        var handler = mock(AccountEventSourcingHandler.class);
        when(handler.getById(any())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> new CloseAccountService(handler).close(UUID.randomUUID()));
        verify(handler, never()).save(any());
    }

    private Account openedAccount(UUID accountId) {
        var account = Account.open(new AccountId(accountId), UUID.randomUUID(), "USD");
        account.markChangesAsCommitted();
        return account;
    }
}
