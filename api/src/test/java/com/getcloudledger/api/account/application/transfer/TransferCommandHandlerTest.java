package com.getcloudledger.api.account.application.transfer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("TransferCommandHandler")
class TransferCommandHandlerTest {

    @Test
    @DisplayName("handle | delegates to TransferService with the command params")
    void handle_delegates_to_transfer_service() {
        var service = mock(TransferService.class);
        var handler = new TransferCommandHandler(service);

        var sourceId = UUID.randomUUID();
        var destinationId = UUID.randomUUID();
        var amount = new BigDecimal("300.00");
        var transferId = UUID.randomUUID();
        handler.handle(new TransferCommand(sourceId, destinationId, amount, transferId));

        verify(service).transfer(sourceId, destinationId, amount, transferId);
    }
}
