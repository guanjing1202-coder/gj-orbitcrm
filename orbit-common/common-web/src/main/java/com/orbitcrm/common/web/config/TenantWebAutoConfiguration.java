package com.orbitcrm.common.web.config;

import com.orbitcrm.common.core.tenant.TenantDomainResolver;
import com.orbitcrm.common.web.filter.TenantResolveFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.stream.Collectors;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(TenantWebAutoConfiguration.TenantWebProperties.class)
public class TenantWebAutoConfiguration {
    @Bean
    public TenantResolveFilter tenantResolveFilter(TenantWebProperties properties,
                                                   ObjectProvider<TenantDomainResolver> tenantDomainResolvers) {
        return new TenantResolveFilter(
                properties.getRootDomain(),
                tenantDomainResolvers.orderedStream().collect(Collectors.toList()));
    }

    @ConfigurationProperties(prefix = "orbit.tenant")
    public static class TenantWebProperties {
        private String rootDomain = "orbitcrm.com";

        public String getRootDomain() {
            return rootDomain;
        }

        public void setRootDomain(String rootDomain) {
            this.rootDomain = rootDomain;
        }
    }
}
