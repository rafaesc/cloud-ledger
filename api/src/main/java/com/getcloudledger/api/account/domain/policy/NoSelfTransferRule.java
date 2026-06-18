package com.getcloudledger.api.account.domain.policy;

import com.getcloudledger.api.account.domain.exception.TransferNotAllowedException;
import com.getcloudledger.api.account.domain.model.Account;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Order(1)
public class NoSelfTransferRule implements TransferRule {

    @Override
    public void enforce(Account source, Account destination, BigDecimal amount) {
        if (source.getId().equals(destination.getId())) {
            throw new TransferNotAllowedException(
                    "Self-transfer is not allowed: accountId=" + source.getId());
        }
    }
}
