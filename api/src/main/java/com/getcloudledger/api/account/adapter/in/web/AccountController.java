package com.getcloudledger.api.account.adapter.in.web;

import com.getcloudledger.api.account.adapter.in.web.request.AmountRequest;
import com.getcloudledger.api.account.adapter.in.web.request.OpenAccountRequest;
import com.getcloudledger.api.account.application.close.CloseAccountCommand;
import com.getcloudledger.api.account.application.deposit.DepositCommand;
import com.getcloudledger.api.account.application.freeze.FreezeAccountCommand;
import com.getcloudledger.api.account.application.open.OpenAccountCommand;
import com.getcloudledger.api.account.application.withdraw.WithdrawCommand;
import com.getcloudledger.api.shared.domain.DomainError;
import com.getcloudledger.api.shared.domain.bus.command.CommandBus;
import com.getcloudledger.api.shared.domain.bus.query.QueryBus;
import com.getcloudledger.api.shared.spring.ApiController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.UUID;

@RestController
@RequestMapping("/v1/accounts")
public class AccountController extends ApiController {

    public AccountController(CommandBus commandBus) {
        super(null, commandBus);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void openAccount(
            @RequestHeader("X-User-Id") @NotNull UUID userId,
            @RequestBody @Valid OpenAccountRequest request) {
        dispatch(new OpenAccountCommand(request.accountId(), userId, request.currency()));
    }

    @PostMapping("/{accountId}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public void deposit(
            @PathVariable UUID accountId,
            @RequestBody @Valid AmountRequest request) {
        dispatch(new DepositCommand(accountId, request.amount()));
    }

    @PostMapping("/{accountId}/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    public void withdraw(
            @PathVariable UUID accountId,
            @RequestBody @Valid AmountRequest request) {
        dispatch(new WithdrawCommand(accountId, request.amount()));
    }

    @PostMapping("/{accountId}/freeze")
    @ResponseStatus(HttpStatus.OK)
    public void freeze(@PathVariable UUID accountId) {
        dispatch(new FreezeAccountCommand(accountId));
    }

    @PostMapping("/{accountId}/close")
    @ResponseStatus(HttpStatus.OK)
    public void close(@PathVariable UUID accountId) {
        dispatch(new CloseAccountCommand(accountId));
    }

    @Override
    public HashMap<Class<? extends DomainError>, HttpStatus> errorMapping() {
        return new HashMap<>();
    }
}
