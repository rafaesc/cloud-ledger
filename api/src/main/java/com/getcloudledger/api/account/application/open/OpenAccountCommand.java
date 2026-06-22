package com.getcloudledger.api.account.application.open;

import com.getcloudledger.api.shared.domain.bus.command.Command;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class OpenAccountCommand implements Command {
    private final UUID accountId;
    private final String ownerId;
    private final String currency;
}
