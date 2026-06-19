package com.getcloudledger.api.account.application.transfer;

import com.getcloudledger.api.shared.domain.bus.command.Command;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class TransferCommand implements Command {
    private final UUID sourceAccountId;
    private final UUID destinationAccountId;
    private final BigDecimal amount;
    private final UUID transferId;
}
