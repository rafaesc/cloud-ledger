package com.getcloudledger.api.account.domain.service;

import com.getcloudledger.api.account.domain.model.Account;
import com.getcloudledger.api.shared.domain.bus.event.DomainEvent;

/**
 * Strategy that attaches a single out-of-band attribute to an uncommitted event before it is stored,
 * using the live aggregate as context. Enrichers run only on the SQS wire format (the event-store
 * payload stays pure). Add a new dynamic attribute by adding a new {@code @Component} — no changes to
 * {@link AccountEventSourcingHandler}. Mirrors the {@code TransferRule}/{@code TransferPolicy} pattern.
 */
public interface AccountEventEnricher {

    void enrich(DomainEvent event, Account account);
}
