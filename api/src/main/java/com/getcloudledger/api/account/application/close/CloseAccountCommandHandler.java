package com.getcloudledger.api.account.application.close;

import com.getcloudledger.api.shared.domain.bus.command.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CloseAccountCommandHandler implements CommandHandler<CloseAccountCommand> {

    private final CloseAccountService closeAccountService;

    @Override
    public void handle(CloseAccountCommand command) {
        closeAccountService.close(command.getAccountId());
    }
}
