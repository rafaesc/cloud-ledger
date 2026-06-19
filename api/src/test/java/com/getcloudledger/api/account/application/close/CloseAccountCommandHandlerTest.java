package com.getcloudledger.api.account.application.close;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("CloseAccountCommandHandler")
class CloseAccountCommandHandlerTest {

    @Test
    @DisplayName("handle | delegates to CloseAccountService with the command params")
    void handle_delegates_to_close_service() {
        var service = mock(CloseAccountService.class);
        var handler = new CloseAccountCommandHandler(service);

        var accountId = UUID.randomUUID();
        handler.handle(new CloseAccountCommand(accountId));

        verify(service).close(accountId);
    }
}
