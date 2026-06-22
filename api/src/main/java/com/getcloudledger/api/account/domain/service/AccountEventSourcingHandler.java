package com.getcloudledger.api.account.domain.service;

import com.getcloudledger.api.account.domain.model.Account;
import com.getcloudledger.api.account.domain.port.out.BalanceCache;
import com.getcloudledger.api.account.domain.valueobject.AccountId;
import com.getcloudledger.api.shared.domain.bus.event.DomainEvent;
import com.getcloudledger.api.shared.domain.service.EventStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AccountEventSourcingHandler {

    private final EventStore eventStore;
    private final BalanceCache balanceCache;
    private final List<AccountEventEnricher> enrichers;

    @Transactional
    public void save(Account aggregate) {
        var uncommittedEvents = aggregate.pullUncommittedChanges();
        enrich(uncommittedEvents, aggregate);
        eventStore.saveEvents(
                aggregate.getId().getValue(),
                aggregate.aggregateType(),
                uncommittedEvents,
                aggregate.getVersion());
        aggregate.markChangesAsCommitted();
        balanceCache.put(aggregate.getId().getValue(), aggregate.getBalance());
    }

    private void enrich(List<DomainEvent> events, Account aggregate) {
        for (var event : events) {
            for (var enricher : enrichers) {
                enricher.enrich(event, aggregate);
            }
        }
    }

    public Optional<Account> getById(AccountId accountId) {
        var eventStream = eventStore.getEvents(accountId.getValue());

        if (eventStream == null || eventStream.isEmpty()) {
            return Optional.empty();
        }

        var aggregate = new Account();
        aggregate.replayEvents(eventStream);

        var latestVersion = eventStream.stream()
                .map(DomainEvent::getVersion)
                .max(Comparator.naturalOrder());
        aggregate.setVersion(latestVersion.get());

        return Optional.of(aggregate);
    }
}
