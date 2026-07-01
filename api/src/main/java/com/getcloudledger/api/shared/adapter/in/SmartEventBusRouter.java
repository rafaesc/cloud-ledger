package com.getcloudledger.api.shared.adapter.in;

import com.getcloudledger.api.shared.adapter.out.entities.OutboxEntity;
import com.getcloudledger.api.shared.adapter.out.repository.JpaOutboxRepository;
import com.getcloudledger.api.shared.domain.bus.event.BaseEvent;
import com.getcloudledger.api.shared.domain.bus.event.DomainEvent;
import com.getcloudledger.api.shared.domain.bus.event.DomainEventJsonSerializer;
import com.getcloudledger.api.shared.domain.bus.event.EventBus;
import com.getcloudledger.api.shared.tracing.TraceContextSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Service
@Primary
@Slf4j
@RequiredArgsConstructor
public class SmartEventBusRouter implements EventBus {

    private final JpaOutboxRepository outboxRepository;
    private final PlatformTransactionManager transactionManager;
    @Qualifier("sqs")
    private final EventBus sqsEventBus;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String aggregateType, List<? extends BaseEvent> events) {
        // Serialize inside the transaction so payloads capture enricher-stamped meta
        // (e.g. balance_after) at the moment the aggregate state is consistent.
        // Snapshot the event list: the caller's aggregate clears its internal list on
        // markChangesAsCommitted(), which fires before afterCommit(). Without a copy,
        // afterCommit() would see an empty list.
        var snapshot = List.copyOf(events);

        // Capture the active trace context NOW, while we are still on the request thread inside
        // the caller's transaction. If these rows later fall back to the outbox, the poller will
        // re-inject this context so the trace continues through the relay. Empty/no-op when the
        // ADOT agent is not attached (local, tests).
        var carrier = TraceContextSupport.currentCarrier();
        var traceparent = carrier.get("traceparent");
        var tracestate = carrier.get("tracestate");

        var outboxRows = snapshot.stream()
                .map(e -> {
                    var row = new OutboxEntity(
                            e.getEventId(),
                            DomainEventJsonSerializer.serialize((DomainEvent) e),
                            e.getSequenceNumber());
                    row.setTraceparent(traceparent);
                    row.setTracestate(tracestate);
                    return row;
                })
                .toList();

        log.info("Registering afterCommit SQS sync for aggregateType={} events={}", aggregateType, snapshot.size());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("afterCommit: attempting SQS publish for aggregateType={} events={}",
                        aggregateType, snapshot.size());
                try {
                    sqsEventBus.publish(aggregateType, snapshot);
                } catch (Exception ex) {
                    log.warn("SQS publish failed for aggregateType={}, writing {} row(s) to outbox",
                            aggregateType, outboxRows.size(), ex);
                    // REQUIRES_NEW is mandatory here: the committed JPA session is still
                    // bound to the thread during afterCommit(). REQUIRED would join that
                    // dead session and silently discard writes. REQUIRES_NEW suspends it
                    // first, opens a genuinely fresh connection, then resumes on exit.
                    var fallbackTx = new TransactionTemplate(transactionManager);
                    fallbackTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    fallbackTx.execute(status -> {
                        outboxRepository.saveAll(outboxRows);
                        return null;
                    });
                }
            }
        });
    }
}
