package com.getcloudledger.api.account.domain.valueobject;

import com.getcloudledger.api.shared.domain.valueobject.BaseId;

import java.util.UUID;

public final class AccountId extends BaseId<UUID> {

    public AccountId(UUID value) {
        super(value);
    }

    public static AccountId generate() {
        return new AccountId(UUID.randomUUID());
    }

    public static AccountId of(UUID value) {
        return new AccountId(value);
    }

    @Override
    public String toString() {
        return getValue().toString();
    }
}
