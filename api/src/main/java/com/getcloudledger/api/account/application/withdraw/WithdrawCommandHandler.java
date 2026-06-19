package com.getcloudledger.api.account.application.withdraw;

import com.getcloudledger.api.shared.domain.bus.command.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WithdrawCommandHandler implements CommandHandler<WithdrawCommand> {

    private final WithdrawService withdrawService;

    @Override
    public void handle(WithdrawCommand command) {
        withdrawService.withdraw(command.getAccountId(), command.getAmount());
    }
}
