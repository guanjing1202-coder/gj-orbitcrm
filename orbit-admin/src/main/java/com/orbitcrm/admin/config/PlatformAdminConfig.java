package com.orbitcrm.admin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlatformAdminConfig {
    @Bean
    public FilterRegistrationBean<PlatformAdminApiKeyFilter> platformAdminApiKeyFilter(
            @Value("${orbit.admin.api-key}") String apiKey) {
        FilterRegistrationBean<PlatformAdminApiKeyFilter> registration =
                new FilterRegistrationBean<PlatformAdminApiKeyFilter>(new PlatformAdminApiKeyFilter(apiKey));
        registration.addUrlPatterns("/api/admin/*");
        registration.setOrder(1);
        return registration;
    }
}
