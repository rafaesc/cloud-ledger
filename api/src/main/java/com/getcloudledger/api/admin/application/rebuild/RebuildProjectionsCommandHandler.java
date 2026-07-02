package com.getcloudledger.api.admin.application.rebuild;

import com.getcloudledger.api.shared.domain.bus.command.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RebuildProjectionsCommandHandler implements CommandHandler<RebuildProjectionsCommand> {

    private final ProjectionRebuildService rebuildService;

    @Override
    public void handle(RebuildProjectionsCommand command) {
        rebuildService.start(command.getJobId(), command.getAccountId());
    }
}
