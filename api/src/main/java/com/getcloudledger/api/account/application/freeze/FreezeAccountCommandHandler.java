package com.getcloudledger.api.account.application.freeze;

import com.getcloudledger.api.shared.domain.bus.command.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FreezeAccountCommandHandler implements CommandHandler<FreezeAccountCommand> {

    private final FreezeAccountService freezeAccountService;

    @Override
    public void handle(FreezeAccountCommand command) {
        freezeAccountService.freeze(command.getAccountId());
    }
}
