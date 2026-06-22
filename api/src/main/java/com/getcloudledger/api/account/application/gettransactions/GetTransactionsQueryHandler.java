package com.getcloudledger.api.account.application.gettransactions;

import com.getcloudledger.api.account.domain.port.out.AccountProjectionPort;
import com.getcloudledger.api.shared.domain.bus.query.QueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class GetTransactionsQueryHandler implements QueryHandler<GetTransactionsQuery, GetTransactionsResponse> {

    private final AccountProjectionPort projectionPort;

    @Override
    public GetTransactionsResponse handle(GetTransactionsQuery query) {
        String startSK = decodeCursor(query.getCursor());

        var page = projectionPort.findTransactions(
                query.getAccountId(),
                query.getLimit(),
                startSK,
                query.isAscending()
        );

        var items = page.items().stream()
                .map(v -> new GetTransactionsResponse.TransactionItem(
                        v.sequenceNumber(),
                        v.eventType(),
                        v.direction(),
                        v.amountCents(),
                        v.counterpartAccountId(),
                        v.transferId(),
                        v.eventId(),
                        v.occurredAt()
                ))
                .toList();

        return new GetTransactionsResponse(query.getAccountId().toString(), items, page.nextCursor());
    }

    private String decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        return new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8);
    }
}
