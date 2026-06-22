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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        var ownerId = UUID.randomUUID().toString();

        var event = new AccountOpened(aggregateId, ownerId, "USD");
        event.setVersion(0);

        adapter.save(aggregateId, "account", event);

        List<DomainEvent> events = adapter.findAllByAggregateId(aggregateId);
        assertFalse(events.isEmpty());

        var result = events.getFirst();
        assertInstanceOf(AccountOpened.class, result);
        var deserialized = (AccountOpened) result;
        assertEquals(aggregateId, deserialized.getAggregateId());
        assertEquals(ownerId, deserialized.getOwnerId());
        assertEquals("USD", deserialized.getCurrency());
    }

    @Test
    @DisplayName("findAll | returns only events belonging to the given aggregateId")
    void find_filtersByAggregateId() {
        var aggregateId = UUID.randomUUID();
        var otherAggregateId = UUID.randomUUID();
        var ownerId = UUID.randomUUID().toString();

        var eventA = new AccountOpened(aggregateId, ownerId, "USD");
        eventA.setVersion(0);
        var eventB = new AccountOpened(otherAggregateId, ownerId, "EUR");
        eventB.setVersion(0);

        adapter.save(aggregateId, "account", eventA);
        adapter.save(otherAggregateId, "account", eventB);

        List<DomainEvent> events = adapter.findAllByAggregateId(aggregateId);
        assertEquals(1, events.size());

        var deserialized = (AccountOpened) events.getFirst();
        assertEquals("USD", deserialized.getCurrency());
    }

    @Test
    @DisplayName("findAll | returns events sorted by ascending version")
    void find_returnsEventsInVersionOrder() {
        var aggregateId = UUID.randomUUID();
        var ownerId = UUID.randomUUID().toString();

        var opened = new AccountOpened(aggregateId, ownerId, "USD");
        opened.setVersion(0);
        var deposited = new MoneyDeposited(aggregateId, ownerId, new BigDecimal("100.00"));
        deposited.setVersion(1);

        adapter.save(aggregateId, "account", opened);
        adapter.save(aggregateId, "account", deposited);

        List<DomainEvent> events = adapter.findAllByAggregateId(aggregateId);
        assertEquals(2, events.size());
        assertInstanceOf(AccountOpened.class, events.get(0));
        assertInstanceOf(MoneyDeposited.class, events.get(1));
    }

    @Test
    @DisplayName("save | persists aggregateType and eventName correctly")
    void save_persistsAggregateTypeAndEventName() {
        var aggregateId = UUID.randomUUID();
        var ownerId = UUID.randomUUID().toString();

        var event = new AccountOpened(aggregateId, ownerId, "GBP");
        event.setVersion(0);

        adapter.save(aggregateId, "account", event);

        Sort sort = Sort.by(Sort.Direction.ASC, "version");
        List<DomainEventEntity> stored = jpaRepository.findAllByAggregateId(aggregateId, sort);
        assertFalse(stored.isEmpty());

        var entity = stored.getFirst();
        assertEquals("account", entity.getAggregateType());
        assertEquals("AccountOpened", entity.getEventName());
        assertNotNull(entity.getPayload());
    }

    @Test
    @DisplayName("save | stamps the returned event with the DB-generated sequence_number")
    void save_stampsSequenceNumberOnEvent() {
        var aggregateId = UUID.randomUUID();
        var ownerId = UUID.randomUUID().toString();

        var event = new AccountOpened(aggregateId, ownerId, "USD");
        event.setVersion(0);

        var saved = adapter.save(aggregateId, "account", event);

        assertNotNull(saved.getSequenceNumber(), "sequence_number must be populated after save");
    }

    @Test
    @DisplayName("save | sequence_numbers are strictly increasing across events")
    void save_sequenceNumbersAreStrictlyIncreasing() {
        var ownerId = UUID.randomUUID().toString();

        var first = new AccountOpened(UUID.randomUUID(), ownerId, "USD");
        first.setVersion(0);
        var second = new AccountOpened(UUID.randomUUID(), ownerId, "EUR");
        second.setVersion(0);

        adapter.save(first.getAggregateId(), "account", first);
        adapter.save(second.getAggregateId(), "account", second);

        assertTrue(second.getSequenceNumber() > first.getSequenceNumber(),
                "Each event must receive a higher sequence_number than the previous one");
    }
}
