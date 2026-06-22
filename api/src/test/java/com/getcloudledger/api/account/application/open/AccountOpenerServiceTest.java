package com.getcloudledger.api.account.application.open;

import com.getcloudledger.api.account.domain.model.Account;
import com.getcloudledger.api.account.domain.model.AccountStatus;
import com.getcloudledger.api.account.domain.port.out.AccountRegistryPort;
import com.getcloudledger.api.account.domain.service.AccountEventSourcingHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("AccountOpenerService")
class AccountOpenerServiceTest {

    @Test
    @DisplayName("open | creates ACTIVE account with ZERO balance and saves it via event sourcing handler")
    void open_creates_active_account_and_saves() {
        var eventSourcingHandler = mock(AccountEventSourcingHandler.class);
        var service = new AccountOpenerService(eventSourcingHandler, mock(AccountRegistryPort.class));

        var accountId = UUID.randomUUID();
        var ownerId = UUID.randomUUID().toString();
        service.open(accountId, ownerId, "EUR");

        var captor = ArgumentCaptor.forClass(Account.class);
        verify(eventSourcingHandler).save(captor.capture());
        var saved = captor.getValue();

        assertEquals(accountId, saved.getId().getValue());
        assertEquals(ownerId, saved.getOwnerId());
        assertEquals("EUR", saved.getCurrency());
        assertEquals(AccountStatus.ACTIVE, saved.getStatus());
        assertEquals(BigDecimal.ZERO, saved.getBalance());
    }

    @Test
    @DisplayName("open | account has one uncommitted AccountOpened event before save is called")
    void open_produces_one_uncommitted_event() {
        var eventSourcingHandler = mock(AccountEventSourcingHandler.class);
        var service = new AccountOpenerService(eventSourcingHandler, mock(AccountRegistryPort.class));

        var captor = ArgumentCaptor.forClass(Account.class);
        service.open(UUID.randomUUID(), UUID.randomUUID().toString(), "USD");

        verify(eventSourcingHandler).save(captor.capture());
        assertEquals(1, captor.getValue().pullUncommittedChanges().size());
    }
}
