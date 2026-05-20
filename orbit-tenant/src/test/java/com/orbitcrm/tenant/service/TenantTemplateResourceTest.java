package com.orbitcrm.tenant.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantTemplateResourceTest {
    @Test
    void tenantSchemaTemplateIsAvailableOnClasspath() {
        ClassPathResource resource = new ClassPathResource("database/tenant-template/001_tenant_schema.sql");

        assertTrue(resource.exists(), "tenant schema template should be packaged for provisioning");
    }
}
