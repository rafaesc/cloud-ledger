package com.getcloudledger.api.shared.adapter.out.repository;

import com.getcloudledger.api.AbstractIntegrationTest;
import com.getcloudledger.api.account.domain.event.AccountOpened;
import com.getcloudledger.api.account.domain.event.MoneyDeposited;
import com.getcloudledger.api.shared.adapter.out.entities.DomainEventEntity;
import com.getcloudledger.api.shared.domain.bus.event.DomainEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("JpaDomainEventRepositoryAdapter (integration)")
class JpaDomainEventRepositoryAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private JpaDomainEventRepositoryAdapter<DomainEvent> adapter;

    @Autowired
    private JpaDomainEventRepository jpaRepository;

    @Test
    @DisplayName("save + findAll | deserializes persisted event with correct fields")
    void saveAndFind_returnsDeserializedEvent() {
        var aggregateId = UUID.randomUUID();
        var accountId = UUID.randomUUID();

        var event = new AccountOpened(aggregateId, accountId, "USD");
        event.setVersion(0);

        adapter.save(aggregateId, "account", event, null);

        List<DomainEvent> events = adapter.findAllByAggregateId(aggregateId);
        assertFalse(events.isEmpty());

        var result = events.getFirst();
        assertInstanceOf(AccountOpened.class, result);
        var deserialized = (AccountOpened) result;
        assertEquals(aggregateId, deserialized.getAggregateId());
        assertEquals(accountId, deserialized.getAccountId());
        assertEquals("USD", deserialized.getCurrency());
    }

    @Test
    @DisplayName("findAll | returns only events belonging to the given aggregateId")
    void find_filtersByAggregateId() {
        var aggregateId = UUID.randomUUID();
        var otherAggregateId = UUID.randomUUID();
        var accountId = UUID.randomUUID();

        var eventA = new AccountOpened(aggregateId, accountId, "USD");
        eventA.setVersion(0);
        var eventB = new AccountOpened(otherAggregateId, accountId, "EUR");
        eventB.setVersion(0);

        adapter.save(aggregateId, "account", eventA, null);
        adapter.save(otherAggregateId, "account", eventB, null);

        List<DomainEvent> events = adapter.findAllByAggregateId(aggregateId);
        assertEquals(1, events.size());

        var deserialized = (AccountOpened) events.getFirst();
        assertEquals("USD", deserialized.getCurrency());
    }

    @Test
    @DisplayName("findAll | returns events sorted by ascending version")
    void find_returnsEventsInVersionOrder() {
        var aggregateId = UUID.randomUUID();
        var accountId = UUID.randomUUID();

        var opened = new AccountOpened(aggregateId, accountId, "USD");
        opened.setVersion(0);
        var deposited = new MoneyDeposited(aggregateId, accountId, new BigDecimal("100.00"));
        deposited.setVersion(1);

        adapter.save(aggregateId, "account", opened, null);
        adapter.save(aggregateId, "account", deposited, null);

        List<DomainEvent> events = adapter.findAllByAggregateId(aggregateId);
        assertEquals(2, events.size());
        assertInstanceOf(AccountOpened.class, events.get(0));
        assertInstanceOf(MoneyDeposited.class, events.get(1));
    }

    @Test
    @DisplayName("save | persists aggregateType and eventName correctly")
    void save_persistsAggregateTypeAndEventName() {
        var aggregateId = UUID.randomUUID();
        var accountId = UUID.randomUUID();

        var event = new AccountOpened(aggregateId, accountId, "GBP");
        event.setVersion(0);

        adapter.save(aggregateId, "account", event, null);

        Sort sort = Sort.by(Sort.Direction.ASC, "version");
        List<DomainEventEntity> stored = jpaRepository.findAllByAggregateId(aggregateId, sort);
        assertFalse(stored.isEmpty());

        var entity = stored.getFirst();
        assertEquals("account", entity.getAggregateType());
        assertEquals("account-opened", entity.getEventName());
        assertNotNull(entity.getPayload());
    }
}
