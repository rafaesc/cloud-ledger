package com.getcloudledger.api.shared.adapter.in;

import com.getcloudledger.api.shared.adapter.out.entities.OutboxEntity;
import com.getcloudledger.api.shared.adapter.out.repository.JpaOutboxRepository;
import com.getcloudledger.api.shared.domain.bus.event.BaseEvent;
import com.getcloudledger.api.shared.domain.bus.event.EventBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Primary
@Slf4j
@RequiredArgsConstructor
public class SmartEventBusRouter implements EventBus {

    private final JpaOutboxRepository outboxRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String aggregateType, List<? extends BaseEvent> events) {
        for (var event : events) {
            outboxRepository.save(new OutboxEntity(event.getEventId(), null));
        }
        log.debug("Wrote {} outbox row(s) for aggregateType={}", events.size(), aggregateType);
    }
}
