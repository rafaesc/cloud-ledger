package com.getcloudledger.api.account.application.open;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("OpenAccountCommandHandler")
class OpenAccountCommandHandlerTest {

    @Test
    @DisplayName("handle | delegates to AccountOpenerService with the command params")
    void handle_delegates_to_opener_service() {
        var service = mock(AccountOpenerService.class);
        var handler = new OpenAccountCommandHandler(service);

        var accountId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        handler.handle(new OpenAccountCommand(accountId, userId, "USD"));

        verify(service).open(accountId, userId, "USD");
    }
}
