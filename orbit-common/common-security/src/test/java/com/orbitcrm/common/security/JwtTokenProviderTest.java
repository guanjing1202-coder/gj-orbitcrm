package com.orbitcrm.common.security;

import com.orbitcrm.common.core.tenant.TenantConstants;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtTokenProviderTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void createTokenIncludesTenantUserIdAndUsernameClaims() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60000L);

        String token = provider.createToken("demo-company", "42", "admin");
        Claims claims = provider.parse(token);

        assertEquals("42", claims.getSubject());
        assertEquals("demo-company", claims.get(TenantConstants.TENANT_CLAIM));
        assertEquals("admin", claims.get("username"));
    }
}
