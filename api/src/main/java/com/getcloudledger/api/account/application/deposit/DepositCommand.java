package com.getcloudledger.api.account.application.deposit;

import com.getcloudledger.api.shared.domain.bus.command.Command;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class DepositCommand implements Command {
    private final UUID accountId;
    private final BigDecimal amount;
}
