package com.getcloudledger.api.shared.spring;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * SpEL security bean for {@code @PreAuthorize} on admin endpoints. Cognito's {@code scope} claim is
 * mapped by Spring's resource server to {@code SCOPE_<scope>} authorities, so the {@code api/admin}
 * scope surfaces as {@code SCOPE_https://api.getcloudledger.com/admin}. Equivalent to
 * {@code hasAuthority('SCOPE_https://api.getcloudledger.com/admin')}, wrapped in a bean so the
 * scope string lives in one place.
 */
@Component("adminSecurity")
public class AdminSecurityService {

    public static final String ADMIN_AUTHORITY = "SCOPE_https://api.getcloudledger.com/admin";

    public boolean hasAdminScope(Authentication auth) {
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ADMIN_AUTHORITY::equals);
    }
}
