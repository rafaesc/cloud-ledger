package com.getcloudledger.api.account.application.deposit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("DepositCommandHandler")
class DepositCommandHandlerTest {

    @Test
    @DisplayName("handle | delegates to DepositService with the command params")
    void handle_delegates_to_deposit_service() {
        var service = mock(DepositService.class);
        var handler = new DepositCommandHandler(service);

        var accountId = UUID.randomUUID();
        var amount = new BigDecimal("100.00");
        handler.handle(new DepositCommand(accountId, amount));

        verify(service).deposit(accountId, amount);
    }
}
