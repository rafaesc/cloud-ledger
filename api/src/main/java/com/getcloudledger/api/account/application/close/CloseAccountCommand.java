package com.getcloudledger.api.account.application.close;

import com.getcloudledger.api.shared.domain.bus.command.Command;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class CloseAccountCommand implements Command {
    private final UUID accountId;
}
