package com.getcloudledger.api.account.domain.port.out;

import java.util.List;
import java.util.UUID;

public interface AccountProjectionPort {

    record AccountStateView(
            String accountId,
            String ownerId,
            String status,
            String currency,
            long version,
            String openedAt,
            String frozenAt,
            String closedAt
    ) {}

    record AccountBalanceView(
            String accountId,
            long balanceCents,
            String currency,
            long version,
            String updatedAt
    ) {}

    record TransactionView(
            long sequenceNumber,
            String eventType,
            String direction,
            long amountCents,
            String counterpartAccountId,
            String transferId,
            String eventId,
            String occurredAt
    ) {}

    record TransactionPage(List<TransactionView> items, String nextCursor) {}

    record AccountSummaryView(
            String accountId,
            String status,
            String currency,
            String openedAt
    ) {}

    AccountStateView findAccountState(UUID accountId);

    AccountBalanceView findAccountBalance(UUID accountId);

    TransactionPage findTransactions(UUID accountId, int limit, String startSK, boolean ascending);

    List<AccountSummaryView> listByOwner(String ownerId);
}
