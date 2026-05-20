package com.orbitcrm.auth.config;

import com.orbitcrm.common.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {
    @Bean
    public JwtTokenProvider jwtTokenProvider(
            @Value("${orbit.jwt.secret}") String secret,
            @Value("${orbit.jwt.ttl-millis}") long ttlMillis) {
        return new JwtTokenProvider(secret, ttlMillis);
    }
}
