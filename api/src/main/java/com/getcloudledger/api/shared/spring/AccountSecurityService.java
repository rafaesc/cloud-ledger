package com.getcloudledger.api.shared.spring;

import com.getcloudledger.api.account.domain.port.out.AccountRegistryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("accountSecurity")
@RequiredArgsConstructor
public class AccountSecurityService {

    private final AccountRegistryPort accountRegistryPort;

    public boolean isOwner(UUID accountId, Authentication auth) {
        var jwt = (Jwt) auth.getPrincipal();
        return accountRegistryPort.isOwner(accountId.toString(), jwt.getSubject());
    }
}
