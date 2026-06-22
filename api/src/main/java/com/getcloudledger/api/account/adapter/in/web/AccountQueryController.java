package com.getcloudledger.api.account.adapter.in.web;

import com.getcloudledger.api.account.application.getaccount.GetAccountQuery;
import com.getcloudledger.api.account.application.getaccount.GetAccountResponse;
import com.getcloudledger.api.account.application.getbalance.GetBalanceQuery;
import com.getcloudledger.api.account.application.getbalance.GetBalanceResponse;
import com.getcloudledger.api.account.application.gettransactions.GetTransactionsQuery;
import com.getcloudledger.api.account.application.gettransactions.GetTransactionsResponse;
import com.getcloudledger.api.account.application.listaccounts.ListAccountsQuery;
import com.getcloudledger.api.account.application.listaccounts.ListAccountsResponse;
import com.getcloudledger.api.shared.domain.DomainError;
import com.getcloudledger.api.shared.domain.bus.query.QueryBus;
import com.getcloudledger.api.shared.spring.ApiController;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.UUID;

@RestController
@RequestMapping("/v1/accounts")
public class AccountQueryController extends ApiController {

    public AccountQueryController(QueryBus queryBus) {
        super(queryBus, null);
    }

    @GetMapping("/{accountId}")
    @PreAuthorize("@accountSecurity.isOwner(#accountId, authentication)")
    public GetAccountResponse getAccount(@PathVariable UUID accountId) {
        return ask(new GetAccountQuery(accountId));
    }

    @GetMapping("/{accountId}/balance")
    @PreAuthorize("@accountSecurity.isOwner(#accountId, authentication)")
    public GetBalanceResponse getBalance(@PathVariable UUID accountId) {
        return ask(new GetBalanceQuery(accountId));
    }

    @GetMapping("/{accountId}/transactions")
    @PreAuthorize("@accountSecurity.isOwner(#accountId, authentication)")
    public GetTransactionsResponse getTransactions(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "desc") String order) {
        int effectiveLimit = Math.min(limit, 200);
        boolean ascending = "asc".equalsIgnoreCase(order);
        return ask(new GetTransactionsQuery(accountId, effectiveLimit, cursor, ascending));
    }

    @GetMapping
    @PreAuthorize("@accountSecurity.isSelf(#ownerId, authentication)")
    public ListAccountsResponse listByOwner(@RequestParam("owner_id") String ownerId) {
        return ask(new ListAccountsQuery(ownerId));
    }

    @Override
    public HashMap<Class<? extends DomainError>, HttpStatus> errorMapping() {
        return new HashMap<>();
    }
}
