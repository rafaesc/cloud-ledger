package com.getcloudledger.api.account.domain.policy;

import com.getcloudledger.api.account.domain.exception.TransferNotAllowedException;
import com.getcloudledger.api.account.domain.model.Account;
import com.getcloudledger.api.account.domain.model.AccountStatus;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Order(2)
public class BothPartiesActiveRule implements TransferRule {

    @Override
    public void enforce(Account source, Account destination, BigDecimal amount) {
        if (source.getStatus() != AccountStatus.ACTIVE) {
            throw new TransferNotAllowedException(
                    "Source account is not active: status=" + source.getStatus());
        }
        if (destination.getStatus() != AccountStatus.ACTIVE) {
            throw new TransferNotAllowedException(
                    "Destination account is not active: status=" + destination.getStatus());
        }
    }
}
