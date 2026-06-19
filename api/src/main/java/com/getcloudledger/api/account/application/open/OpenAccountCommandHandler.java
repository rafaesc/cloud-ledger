package com.getcloudledger.api.account.application.open;

import com.getcloudledger.api.shared.domain.bus.command.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OpenAccountCommandHandler implements CommandHandler<OpenAccountCommand> {

    private final AccountOpenerService accountOpenerService;

    @Override
    public void handle(OpenAccountCommand command) {
        accountOpenerService.open(
                command.getAccountId(),
                command.getUserId(),
                command.getCurrency()
        );
    }
}
