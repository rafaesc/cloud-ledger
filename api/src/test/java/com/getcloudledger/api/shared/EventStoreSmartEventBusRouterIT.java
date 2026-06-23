package com.getcloudledger.api.shared;

import com.getcloudledger.api.AbstractIntegrationTest;
import com.getcloudledger.api.account.domain.event.AccountOpened;
import com.getcloudledger.api.account.domain.event.MoneyDeposited;
import com.getcloudledger.api.shared.adapter.out.entities.DomainEventEntity;
import com.getcloudledger.api.shared.adapter.out.entities.OutboxEntity;
import com.getcloudledger.api.shared.adapter.out.repository.JpaDomainEventRepository;
import com.getcloudledger.api.shared.adapter.out.repository.JpaOutboxRepository;
import com.getcloudledger.api.shared.domain.bus.event.BaseEvent;
import com.getcloudledger.api.shared.domain.bus.event.EventBus;
import com.getcloudledger.api.shared.domain.service.EventStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@DisplayName("EventStore + SmartEventBusRouter (integration)")
class EventStoreSmartEventBusRouterIT extends AbstractIntegrationTest {

    private static final String ACCOUNT = "account";

    @Autowired
    private EventStore eventStore;

    @Autowired
    private JpaDomainEventRepository jpaDomainEventRepository;

    @Autowired
    private JpaOutboxRepository jpaOutboxRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * The real {@link com.getcloudledger.api.shared.adapter.in.SmartEventBusRouter} stays in the
     * context (it is {@code @Primary}); only the downstream SQS hop is mocked so no broker is needed
     * and we can assert exactly when the after-commit publish fires.
     */
    @MockitoBean(name = "sqsEventBus")
    private EventBus sqsEventBus;

    @Test
    @DisplayName("saveEvents | on SQS success: persists events, publishes to SQS after commit, writes no outbox rows")
    void saveEvents_persistsEventsAndPublishesToSqsWithNoOutboxOnSuccess() {
        var aggregateId = UUID.randomUUID();
        var ownerId = UUID.randomUUID();
        var events = List.of(
                opened(aggregateId, ownerId, "USD"),
                deposited(aggregateId, ownerId, "100.00"));

        inTransaction(() -> eventStore.saveEvents(aggregateId, ACCOUNT, events, -1));

        var storedEvents = storedEvents(aggregateId);
        assertEquals(2, storedEvents.size(), "both events must be persisted");
        assertEquals(0, storedEvents.get(0).getVersion());
        assertEquals(1, storedEvents.get(1).getVersion());

        assertEquals(0, outboxRowsFor(events).size(), "no outbox row written when SQS succeeds");

        verify(sqsEventBus, timeout(2000)).publish(eq(ACCOUNT), any());
    }

    @Test
    @DisplayName("saveEvents | on SQS failure: each outbox row carries the event sequence_number and stays unpublished")
    void saveEvents_outboxCarriesSequenceNumberAndIsUnpublished() {
        var aggregateId = UUID.randomUUID();
        var ownerId = UUID.randomUUID();
        var events = List.of(opened(aggregateId, ownerId, "EUR"));

        doThrow(new RuntimeException("SQS unavailable")).when(sqsEventBus).publish(any(), any());
        inTransaction(() -> eventStore.saveEvents(aggregateId, ACCOUNT, events, -1));

        var storedEvent = storedEvents(aggregateId).getFirst();
        var outboxRow = jpaOutboxRepository.findById(storedEvent.getEventId()).orElseThrow();

        assertNotNull(storedEvent.getSequenceNumber(), "event must be stamped with a sequence_number");
        assertEquals(storedEvent.getSequenceNumber(), outboxRow.getSequenceNumber(),
                "outbox row must mirror the event sequence_number");
        assertNull(outboxRow.getPublishedAt(), "fresh outbox rows are unpublished until the relay runs");
        assertNotNull(outboxRow.getCreatedAt(), "created_at must be stamped by the database default");
    }

    @Test
    @DisplayName("saveEvents | on SQS failure: outbox payload is the SQS wire format carrying dynamic attributes, while the event-store payload stays pure")
    void saveEvents_outboxPayloadCarriesDynamicAttributesEventStoreDoesNot() {
        var aggregateId = UUID.randomUUID();
        var ownerId = UUID.randomUUID();
        var deposited = deposited(aggregateId, ownerId, "100.00");
        deposited.putDynamicAttribute("balance_after", "100.00");

        doThrow(new RuntimeException("SQS unavailable")).when(sqsEventBus).publish(any(), any());
        inTransaction(() -> eventStore.saveEvents(aggregateId, ACCOUNT, List.of(deposited), -1));

        var storedEvent = storedEvents(aggregateId).getFirst();
        var outboxRow = jpaOutboxRepository.findById(storedEvent.getEventId()).orElseThrow();

        // outbox.payload == DomainEventJsonSerializer.serialize(...) → balance_after under meta
        assertTrue(outboxRow.getPayload().contains("\"meta\""), "outbox payload must use the wire envelope");
        assertTrue(outboxRow.getPayload().contains("balance_after"),
                "outbox payload must carry the dynamic attribute for the projector");

        // events.payload == DomainEventJsonSerializer.serializePrimitives(...) → pure, no balance_after
        assertFalse(storedEvent.getPayload().contains("balance_after"),
                "the immutable event-store payload must never carry derived dynamic attributes");
    }

    @Test
    @DisplayName("saveEvents | rolls back events, outbox rows and SQS publish when the surrounding transaction fails")
    void saveEvents_rollsBackEverythingWhenTransactionFails() {
        var aggregateId = UUID.randomUUID();
        var ownerId = UUID.randomUUID();
        var events = List.of(opened(aggregateId, ownerId, "USD"));

        assertThrows(IllegalStateException.class, () -> inTransaction(() -> {
            eventStore.saveEvents(aggregateId, ACCOUNT, events, -1);
            throw new IllegalStateException("force rollback after the event store write");
        }));

        assertEquals(0, storedEvents(aggregateId).size(), "no event may survive a rolled-back transaction");
        assertEquals(0, outboxRowsFor(events).size(), "no outbox row may survive a rolled-back transaction");
        verify(sqsEventBus, never()).publish(any(), any());
    }

    @Test
    @DisplayName("saveEvents | propagation MANDATORY rejects the outbox write when there is no active transaction")
    void saveEvents_failsWhenCalledWithoutActiveTransaction() {
        var aggregateId = UUID.randomUUID();
        var ownerId = UUID.randomUUID();
        var events = List.of(opened(aggregateId, ownerId, "USD"));

        // No surrounding transaction: SmartEventBusRouter.publish is Propagation.MANDATORY.
        assertThrows(IllegalTransactionStateException.class,
                () -> eventStore.saveEvents(aggregateId, ACCOUNT, events, -1));

        verify(sqsEventBus, never()).publish(any(), any());
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }

    private List<DomainEventEntity> storedEvents(UUID aggregateId) {
        return jpaDomainEventRepository.findAllByAggregateId(aggregateId, Sort.by(Sort.Direction.ASC, "version"));
    }

    private List<OutboxEntity> outboxRowsFor(List<? extends BaseEvent> events) {
        return jpaOutboxRepository.findAllById(events.stream().map(BaseEvent::getEventId).toList());
    }

    private AccountOpened opened(UUID aggregateId, UUID ownerId, String currency) {
        return new AccountOpened(aggregateId, ownerId.toString(), currency);
    }

    private MoneyDeposited deposited(UUID aggregateId, UUID ownerId, String amount) {
        return new MoneyDeposited(aggregateId, ownerId.toString(), new BigDecimal(amount));
    }
}
