package com.getcloudledger.api.account.application.transfer;

import com.getcloudledger.api.shared.domain.bus.command.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransferCommandHandler implements CommandHandler<TransferCommand> {

    private final TransferService transferService;

    @Override
    public void handle(TransferCommand command) {
        transferService.transfer(
                command.getSourceAccountId(),
                command.getDestinationAccountId(),
                command.getAmount(),
                command.getTransferId()
        );
    }
}
