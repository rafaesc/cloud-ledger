package com.getcloudledger.api.account.domain.model;

import com.getcloudledger.api.account.domain.event.AccountClosed;
import com.getcloudledger.api.account.domain.event.AccountFrozen;
import com.getcloudledger.api.account.domain.event.AccountOpened;
import com.getcloudledger.api.account.domain.event.MoneyDeposited;
import com.getcloudledger.api.account.domain.event.MoneyWithdrawn;
import com.getcloudledger.api.account.domain.event.TransferCredited;
import com.getcloudledger.api.account.domain.event.TransferDebited;
import com.getcloudledger.api.account.domain.event.TransferFailed;
import com.getcloudledger.api.account.domain.exception.InsufficientFundsException;
import com.getcloudledger.api.account.domain.valueobject.AccountId;
import com.getcloudledger.api.shared.domain.entity.AggregateRoot;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class Account extends AggregateRoot<AccountId> {

    private UUID userId;
    private String currency;
    private BigDecimal balance;
    private AccountStatus status;

    public static Account open(AccountId accountId, UUID userId, String currency) {
        var account = new Account();
        account.record(new AccountOpened(accountId.getValue(), userId, currency));
        return account;
    }

    public void deposit(BigDecimal amount) {
        requireActive();
        requirePositive(amount);
        record(new MoneyDeposited(getId().getValue(), userId, amount));
    }

    public void withdraw(BigDecimal amount) {
        requireActive();
        requirePositive(amount);
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(getId(), balance, amount);
        }
        record(new MoneyWithdrawn(getId().getValue(), userId, amount));
    }

    public void debitTransfer(BigDecimal amount, UUID counterpartAccountId, UUID transferId) {
        requireActive();
        requirePositive(amount);
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(getId(), balance, amount);
        }
        record(new TransferDebited(getId().getValue(), userId, amount, counterpartAccountId, transferId));
    }

    public void creditTransfer(BigDecimal amount, UUID counterpartAccountId, UUID transferId) {
        requireActive();
        requirePositive(amount);
        record(new TransferCredited(getId().getValue(), userId, amount, counterpartAccountId, transferId));
    }

    public void failTransfer(BigDecimal amount, UUID counterpartAccountId, UUID transferId, String reason) {
        requirePositive(amount);
        record(new TransferFailed(getId().getValue(), userId, amount, counterpartAccountId, transferId, reason));
    }

    public void freeze() {
        if (status != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Only an ACTIVE account can be frozen, current status: " + status);
        }
        record(new AccountFrozen(getId().getValue(), userId));
    }

    public void close() {
        if (status == AccountStatus.CLOSED) {
            throw new IllegalStateException("Account is already closed");
        }
        record(new AccountClosed(getId().getValue(), userId));
    }

    public void apply(AccountOpened event) {
        setId(new AccountId(event.getAggregateId()));
        this.userId = event.getUserId();
        this.currency = event.getCurrency();
        this.status = AccountStatus.ACTIVE;
        this.balance = BigDecimal.ZERO;
    }

    public void apply(MoneyDeposited event) {
        this.balance = this.balance.add(event.getAmount());
    }

    public void apply(MoneyWithdrawn event) {
        this.balance = this.balance.subtract(event.getAmount());
    }

    public void apply(TransferDebited event) {
        this.balance = this.balance.subtract(event.getAmount());
    }

    public void apply(TransferCredited event) {
        this.balance = this.balance.add(event.getAmount());
    }

    public void apply(TransferFailed event) {
        this.balance = this.balance.add(event.getAmount());
    }

    public void apply(AccountFrozen event) {
        this.status = AccountStatus.FROZEN;
    }

    public void apply(AccountClosed event) {
        this.status = AccountStatus.CLOSED;
    }

    @Override
    public String aggregateType() {
        return "account";
    }

    private void requireActive() {
        if (status != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active, current status: " + status);
        }
    }

    private void requirePositive(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive, got: " + amount);
        }
    }
}
