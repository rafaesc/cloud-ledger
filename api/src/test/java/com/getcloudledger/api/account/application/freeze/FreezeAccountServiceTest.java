package com.getcloudledger.api.account.application.freeze;

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

@DisplayName("FreezeAccountService")
class FreezeAccountServiceTest {

    @Test
    @DisplayName("freeze | transitions account to FROZEN status and saves")
    void freeze_sets_status_to_frozen_and_saves() {
        var handler = mock(AccountEventSourcingHandler.class);
        var service = new FreezeAccountService(handler);

        var accountId = UUID.randomUUID();
        var account = openedAccount(accountId);
        when(handler.getById(new AccountId(accountId))).thenReturn(Optional.of(account));

        service.freeze(accountId);

        var captor = ArgumentCaptor.forClass(Account.class);
        verify(handler).save(captor.capture());
        assertEquals(AccountStatus.FROZEN, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("freeze | throws when account does not exist")
    void freeze_throws_when_account_not_found() {
        var handler = mock(AccountEventSourcingHandler.class);
        when(handler.getById(any())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> new FreezeAccountService(handler).freeze(UUID.randomUUID()));
        verify(handler, never()).save(any());
    }

    private Account openedAccount(UUID accountId) {
        var account = Account.open(new AccountId(accountId), UUID.randomUUID().toString(), "USD");
        account.markChangesAsCommitted();
        return account;
    }
}
