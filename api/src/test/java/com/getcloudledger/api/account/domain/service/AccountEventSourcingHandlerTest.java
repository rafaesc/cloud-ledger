package com.getcloudledger.api.account.domain.service;

import com.getcloudledger.api.account.domain.event.AccountOpened;
import com.getcloudledger.api.account.domain.event.MoneyDeposited;
import com.getcloudledger.api.account.domain.model.Account;
import com.getcloudledger.api.account.domain.model.AccountStatus;
import com.getcloudledger.api.account.domain.valueobject.AccountId;
import com.getcloudledger.api.shared.domain.bus.event.DomainEvent;
import com.getcloudledger.api.shared.domain.bus.event.EventBus;
import com.getcloudledger.api.shared.domain.exception.ConcurrencyException;
import com.getcloudledger.api.shared.domain.port.repository.DomainEventRepository;
import com.getcloudledger.api.shared.domain.service.EventStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.nullable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AccountEventSourcingHandler")
class AccountEventSourcingHandlerTest {

    @Test
    @DisplayName("save | persists uncommitted events with version 0 and clears them after commit")
    void save_persists_uncommitted_events_and_marks_committed() {
        var handler = buildHandler(new ArrayList<>());

        var accountId = AccountId.generate();
        var account = Account.open(accountId, UUID.randomUUID(), "USD");
        assertFalse(account.pullUncommittedChanges().isEmpty());

        handler.save(account, null);

        assertTrue(account.pullUncommittedChanges().isEmpty(),
                "Uncommitted changes must be cleared after save");
    }

    @Test
    @DisplayName("save | first persisted event receives version 0")
    void save_assigns_version_zero_to_first_event() {
        DomainEventRepository<DomainEvent> repo = mock(DomainEventRepository.class);
        EventBus eventBus = mock(EventBus.class);
        var handler = new AccountEventSourcingHandler(new EventStore(repo, eventBus));

        when(repo.findAllByAggregateId(any())).thenReturn(new ArrayList<>());
        when(repo.save(any(), anyString(), any(), nullable(UUID.class)))
                .thenAnswer(inv -> inv.getArgument(2));

        var accountId = AccountId.generate();
        var account = Account.open(accountId, UUID.randomUUID(), "USD");

        handler.save(account, null);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(repo, times(1)).save(eq(accountId.getValue()), eq("account"), captor.capture(), nullable(UUID.class));
        assertIsInstance(AccountOpened.class, captor.getValue());
        assertEquals(0, captor.getValue().getVersion());
    }

    @Test
    @DisplayName("save | forwards idempotency key to the repository")
    void save_forwards_idempotency_key_to_repository() {
        DomainEventRepository<DomainEvent> repo = mock(DomainEventRepository.class);
        EventBus eventBus = mock(EventBus.class);
        var handler = new AccountEventSourcingHandler(new EventStore(repo, eventBus));

        when(repo.findAllByAggregateId(any())).thenReturn(new ArrayList<>());
        when(repo.save(any(), anyString(), any(), any())).thenAnswer(inv -> inv.getArgument(2));

        var idempotencyKey = UUID.randomUUID();
        var account = Account.open(AccountId.generate(), UUID.randomUUID(), "USD");

        handler.save(account, idempotencyKey);

        verify(repo).save(any(), anyString(), any(), eq(idempotencyKey));
    }

    @Test
    @DisplayName("save | throws ConcurrencyException when stored version does not match expected")
    void save_throws_concurrency_exception_when_version_mismatch() {
        DomainEventRepository<DomainEvent> repo = mock(DomainEventRepository.class);
        EventBus eventBus = mock(EventBus.class);
        var handler = new AccountEventSourcingHandler(new EventStore(repo, eventBus));

        var accountId = AccountId.generate();
        var existingEvent = new AccountOpened(accountId.getValue(), UUID.randomUUID(), "USD");
        existingEvent.setVersion(1);
        when(repo.findAllByAggregateId(eq(accountId.getValue()))).thenReturn(List.of(existingEvent));

        var account = Account.open(accountId, UUID.randomUUID(), "USD");
        account.setVersion(0);

        assertThrows(ConcurrencyException.class, () -> handler.save(account, null));
    }

    @Test
    @DisplayName("getById | returns empty when no events exist for aggregate")
    void getById_returns_empty_when_no_events() {
        var handler = buildHandler(List.of());
        var accountId = AccountId.generate();

        var result = handler.getById(accountId);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getById | replays events and sets aggregate to latest version")
    void getById_replays_events_and_sets_latest_version() {
        var accountUuid = UUID.randomUUID();
        var userId = UUID.randomUUID();

        var opened = new AccountOpened(accountUuid, userId, "USD");
        opened.setVersion(0);
        var deposited = new MoneyDeposited(accountUuid, userId, new BigDecimal("250.00"));
        deposited.setVersion(1);

        var handler = buildHandler(List.of(opened, deposited));
        var result = handler.getById(AccountId.of(accountUuid));

        assertTrue(result.isPresent());
        var account = result.get();
        assertEquals(accountUuid, account.getId().getValue());
        assertEquals("USD", account.getCurrency());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertEquals(new BigDecimal("250.00"), account.getBalance());
        assertEquals(1, account.getVersion());
        assertNotNull(account.getUserId());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private AccountEventSourcingHandler buildHandler(List<DomainEvent> storedEvents) {
        DomainEventRepository<DomainEvent> repo = mock(DomainEventRepository.class);
        EventBus eventBus = mock(EventBus.class);
        when(repo.findAllByAggregateId(any())).thenReturn(storedEvents instanceof ArrayList
                ? storedEvents : new ArrayList<>(storedEvents));
        when(repo.save(any(), anyString(), any(), nullable(UUID.class)))
                .thenAnswer(inv -> inv.getArgument(2));
        return new AccountEventSourcingHandler(new EventStore(repo, eventBus));
    }

    private static void assertIsInstance(Class<?> expected, Object actual) {
        assertTrue(expected.isInstance(actual),
                "Expected " + expected.getSimpleName() + " but got " + actual.getClass().getSimpleName());
    }
}
