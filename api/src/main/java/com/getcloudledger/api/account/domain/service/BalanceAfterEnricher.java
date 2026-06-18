package com.getcloudledger.api.account.domain.service;

import com.getcloudledger.api.account.domain.event.BalanceAware;
import com.getcloudledger.api.account.domain.model.Account;
import com.getcloudledger.api.shared.domain.bus.event.DomainEvent;
import org.springframework.stereotype.Component;

/**
 * Stamps {@code balance_after} onto balance-mutating events so downstream projectors can build the
 * balance read model without replaying. Emitted as a string to match the wire format used for money
 * amounts (see each event's {@code toPrimitives()}).
 */
@Component
public class BalanceAfterEnricher implements AccountEventEnricher {

    @Override
    public void enrich(DomainEvent event, Account account) {
        if (event instanceof BalanceAware) {
            event.putDynamicAttribute("balance_after", account.getBalance().toPlainString());
        }
    }
}
