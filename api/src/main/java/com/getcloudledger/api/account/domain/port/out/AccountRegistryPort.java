package com.getcloudledger.api.account.domain.port.out;

public interface AccountRegistryPort {
    void register(String accountId, String ownerId, String currency);
    boolean isOwner(String accountId, String ownerId);
}
