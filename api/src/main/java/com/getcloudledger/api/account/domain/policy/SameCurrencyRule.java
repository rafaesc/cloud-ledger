package com.getcloudledger.api.account.domain.policy;

import com.getcloudledger.api.account.domain.exception.TransferNotAllowedException;
import com.getcloudledger.api.account.domain.model.Account;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Order(3)
public class SameCurrencyRule implements TransferRule {

    @Override
    public void enforce(Account source, Account destination, BigDecimal amount) {
        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw new TransferNotAllowedException(
                    "Currency mismatch: source=" + source.getCurrency()
                    + " destination=" + destination.getCurrency());
        }
    }
}
