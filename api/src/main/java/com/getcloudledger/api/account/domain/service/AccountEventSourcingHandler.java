package com.getcloudledger.api.account.domain.service;

import com.getcloudledger.api.account.domain.model.Account;
import com.getcloudledger.api.account.domain.valueobject.AccountId;
import com.getcloudledger.api.shared.domain.bus.event.DomainEvent;
import com.getcloudledger.api.shared.domain.service.EventStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountEventSourcingHandler {

    private final EventStore eventStore;

    @Transactional
    public void save(Account aggregate, UUID idempotencyKey) {
        eventStore.saveEvents(
                aggregate.getId().getValue(),
                aggregate.aggregateType(),
                aggregate.pullUncommittedChanges(),
                aggregate.getVersion(),
                idempotencyKey);
        aggregate.markChangesAsCommitted();
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
