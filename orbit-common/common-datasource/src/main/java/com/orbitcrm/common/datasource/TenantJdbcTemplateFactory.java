package com.orbitcrm.common.datasource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class TenantJdbcTemplateFactory {
    public JdbcTemplate createTenantJdbcTemplate(TenantDatabaseProperties properties) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("orbit-tenant-" + properties.getTenantCode());
        dataSource.setJdbcUrl(properties.getJdbcUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(decodePasswordCipher(properties.getPassword()));
        return new JdbcTemplate(dataSource);
    }

    private String decodePasswordCipher(String passwordCipher) {
        if (!StringUtils.hasText(passwordCipher)) {
            return "";
        }
        try {
            return new String(Base64.getDecoder().decode(passwordCipher), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return passwordCipher;
        }
    }
}
