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
import com.getcloudledger.api.shared.spring.ApiController;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid OpenAccountRequest request) {
        dispatch(new OpenAccountCommand(request.accountId(), jwt.getSubject(), request.currency()));
    }

    @PostMapping("/{accountId}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@accountSecurity.isOwner(#accountId, authentication)")
    public void deposit(
            @PathVariable UUID accountId,
            @RequestBody @Valid AmountRequest request) {
        dispatch(new DepositCommand(accountId, request.amount()));
    }

    @PostMapping("/{accountId}/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@accountSecurity.isOwner(#accountId, authentication)")
    public void withdraw(
            @PathVariable UUID accountId,
            @RequestBody @Valid AmountRequest request) {
        dispatch(new WithdrawCommand(accountId, request.amount()));
    }

    @PostMapping("/{accountId}/freeze")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("@accountSecurity.isOwner(#accountId, authentication)")
    public void freeze(@PathVariable UUID accountId) {
        dispatch(new FreezeAccountCommand(accountId));
    }

    @PostMapping("/{accountId}/close")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("@accountSecurity.isOwner(#accountId, authentication)")
    public void close(@PathVariable UUID accountId) {
        dispatch(new CloseAccountCommand(accountId));
    }

    @Override
    public HashMap<Class<? extends DomainError>, HttpStatus> errorMapping() {
        return new HashMap<>();
    }
}
