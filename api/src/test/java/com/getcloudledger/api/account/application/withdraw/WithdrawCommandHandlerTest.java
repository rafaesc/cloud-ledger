package com.getcloudledger.api.account.application.withdraw;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("WithdrawCommandHandler")
class WithdrawCommandHandlerTest {

    @Test
    @DisplayName("handle | delegates to WithdrawService with the command params")
    void handle_delegates_to_withdraw_service() {
        var service = mock(WithdrawService.class);
        var handler = new WithdrawCommandHandler(service);

        var accountId = UUID.randomUUID();
        var amount = new BigDecimal("50.00");
        handler.handle(new WithdrawCommand(accountId, amount));

        verify(service).withdraw(accountId, amount);
    }
}
