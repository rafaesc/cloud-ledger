package com.getcloudledger.api.shared.adapter.out.repository;

import com.getcloudledger.api.shared.adapter.out.entities.DomainEventEntity;
import com.getcloudledger.api.shared.domain.Utils;
import com.getcloudledger.api.shared.domain.bus.event.DomainEvent;
import com.getcloudledger.api.shared.domain.bus.event.DomainEventJsonSerializer;
import com.getcloudledger.api.shared.domain.bus.event.EventJsonDeserializer;
import com.getcloudledger.api.shared.domain.port.repository.DomainEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JpaDomainEventRepositoryAdapter<T extends DomainEvent> implements DomainEventRepository<T> {

    private final EventJsonDeserializer deserializer;
    private final JpaDomainEventRepository jpaDomainEventRepository;

    @Override
    @SneakyThrows
    public List<T> findAllByAggregateId(UUID aggregateId) {
        Sort domainEventSort = Sort.by(Sort.Direction.ASC, "version");
        var domainEventEntities = jpaDomainEventRepository.findAllByAggregateId(aggregateId, domainEventSort);

        return domainEventEntities.stream()
                .map(entity -> {
                    try {
                        return (T) deserializer.deserializePrimitives(
                                entity.getEventId().toString(),
                                entity.getUserId().toString(),
                                entity.getAggregateId().toString(),
                                entity.getEventName(),
                                Utils.dateToString(entity.getOccurredOn()),
                                entity.getVersion(),
                                entity.getPayload());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();
    }

    @Override
    public T save(UUID aggregateId, String aggregateType, T domainEvent) {
        var entity = new DomainEventEntity();
        entity.setEventId(domainEvent.getEventId());
        entity.setUserId(domainEvent.getUserId());
        entity.setAggregateId(aggregateId);
        entity.setAggregateType(aggregateType);
        entity.setVersion(domainEvent.getVersion());
        entity.setOccurredOn(Utils.stringToDate(domainEvent.getOccurredOn()));
        entity.setPayload(DomainEventJsonSerializer.serializePrimitives(domainEvent));
        entity.setEventName(Utils.getEventName(domainEvent.getClass()));

        var saved = jpaDomainEventRepository.saveAndFlush(entity);
        domainEvent.setSequenceNumber(saved.getSequenceNumber());
        return domainEvent;
    }
}
