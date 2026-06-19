package com.getcloudledger.api.account.application.freeze;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("FreezeAccountCommandHandler")
class FreezeAccountCommandHandlerTest {

    @Test
    @DisplayName("handle | delegates to FreezeAccountService with the command params")
    void handle_delegates_to_freeze_service() {
        var service = mock(FreezeAccountService.class);
        var handler = new FreezeAccountCommandHandler(service);

        var accountId = UUID.randomUUID();
        handler.handle(new FreezeAccountCommand(accountId));

        verify(service).freeze(accountId);
    }
}
