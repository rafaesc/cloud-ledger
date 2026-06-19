package com.getcloudledger.api.account.application.deposit;

import com.getcloudledger.api.shared.domain.bus.command.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DepositCommandHandler implements CommandHandler<DepositCommand> {

    private final DepositService depositService;

    @Override
    public void handle(DepositCommand command) {
        depositService.deposit(command.getAccountId(), command.getAmount());
    }
}
