package com.orbitcrm.billing.job;

import com.orbitcrm.billing.service.BillingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionExpireJob {
    private final BillingService billingService;

    public SubscriptionExpireJob(BillingService billingService) {
        this.billingService = billingService;
    }

    @Scheduled(cron = "${orbit.billing.expire-cron:0 10 2 * * ?}")
    public void expireSubscriptions() {
        billingService.expireSubscriptions();
    }
}
