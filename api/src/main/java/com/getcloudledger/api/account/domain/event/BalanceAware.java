package com.getcloudledger.api.account.domain.event;

/**
 * Marks a domain event whose emission changes the account balance, making it eligible for the
 * {@code balance_after} dynamic attribute on the SQS wire format. Implement this on any new
 * balance-mutating event — no enricher or handler changes are required.
 */
public interface BalanceAware {
}
