package com.getcloudledger.api.account.application.transfer;

import com.getcloudledger.api.account.domain.policy.TransferPolicy;
import com.getcloudledger.api.account.domain.service.AccountEventSourcingHandler;
import com.getcloudledger.api.account.domain.valueobject.AccountId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final AccountEventSourcingHandler eventSourcingHandler;
    private final TransferPolicy transferPolicy;

    @Transactional
    public void transfer(UUID sourceId, UUID destinationId, BigDecimal amount, UUID transferId) {
        var source = eventSourcingHandler.getById(new AccountId(sourceId))
                .orElseThrow(() -> new IllegalArgumentException("Source account not found: " + sourceId));
        var destination = eventSourcingHandler.getById(new AccountId(destinationId))
                .orElseThrow(() -> new IllegalArgumentException("Destination account not found: " + destinationId));

        transferPolicy.validate(source, destination, amount);

        source.debitTransfer(amount, destinationId, transferId);
        destination.creditTransfer(amount, sourceId, transferId);

        eventSourcingHandler.save(source);
        eventSourcingHandler.save(destination);
    }
}
