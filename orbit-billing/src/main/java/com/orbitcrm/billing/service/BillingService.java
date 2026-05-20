package com.orbitcrm.billing.service;

import com.orbitcrm.billing.api.BillingOrderCreateRequest;
import com.orbitcrm.billing.api.BillingOrderResponse;
import com.orbitcrm.billing.api.PaymentConfirmRequest;
import com.orbitcrm.billing.api.PaymentResponse;
import com.orbitcrm.billing.api.PlanFeatureResponse;
import com.orbitcrm.billing.api.PlanResponse;
import com.orbitcrm.billing.api.SubscriptionResponse;
import com.orbitcrm.common.core.tenant.TenantContext;
import com.orbitcrm.common.log.OperationLog;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class BillingService {
    private final JdbcTemplate jdbcTemplate;

    public BillingService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PlanResponse> listPlans() {
        Map<Long, PlanResponse> plans = new LinkedHashMap<Long, PlanResponse>();
        jdbcTemplate.query(
                "SELECT id, plan_code, plan_name, billing_cycle, price_cent, status " +
                        "FROM platform_plan WHERE status = 'ACTIVE' ORDER BY price_cent, id",
                rs -> {
                    PlanResponse plan = new PlanResponse();
                    plan.setId(rs.getLong("id"));
                    plan.setPlanCode(rs.getString("plan_code"));
                    plan.setPlanName(rs.getString("plan_name"));
                    plan.setBillingCycle(rs.getString("billing_cycle"));
                    plan.setPriceCent(rs.getLong("price_cent"));
                    plan.setStatus(rs.getString("status"));
                    plans.put(plan.getId(), plan);
                });
        if (plans.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        String planIds = joinPlanIds(plans);
        jdbcTemplate.query(
                "SELECT plan_id, feature_key, feature_value, value_type FROM platform_plan_feature " +
                        "WHERE plan_id IN (" + planIds + ") ORDER BY plan_id, feature_key",
                rs -> {
                    PlanResponse plan = plans.get(rs.getLong("plan_id"));
                    if (plan != null) {
                        plan.getFeatures().add(new PlanFeatureResponse(
                                rs.getString("feature_key"),
                                rs.getString("feature_value"),
                                rs.getString("value_type")));
                    }
                });
        return new java.util.ArrayList<PlanResponse>(plans.values());
    }

    public SubscriptionResponse currentSubscription(String tenantCode) {
        String normalizedTenantCode = normalizeTenantCode(tenantCode);
        List<SubscriptionResponse> subscriptions = jdbcTemplate.query(
                "SELECT s.id, t.tenant_code, p.plan_code, p.plan_name, s.status, " +
                        "s.start_time, s.end_time, s.trial_end_time, s.grace_days " +
                        "FROM platform_subscription s " +
                        "JOIN platform_tenant t ON s.tenant_id = t.id " +
                        "JOIN platform_plan p ON s.plan_id = p.id " +
                        "WHERE t.tenant_code = ? ORDER BY s.id DESC LIMIT 1",
                (rs, rowNum) -> {
                    SubscriptionResponse response = new SubscriptionResponse();
                    response.setId(rs.getLong("id"));
                    response.setTenantCode(rs.getString("tenant_code"));
                    response.setPlanCode(rs.getString("plan_code"));
                    response.setPlanName(rs.getString("plan_name"));
                    response.setStatus(rs.getString("status"));
                    response.setStartTime(toLocalDateTime(rs.getTimestamp("start_time")));
                    response.setEndTime(toLocalDateTime(rs.getTimestamp("end_time")));
                    response.setTrialEndTime(toLocalDateTime(rs.getTimestamp("trial_end_time")));
                    response.setGraceDays(rs.getInt("grace_days"));
                    return response;
                },
                normalizedTenantCode
            );
        if (subscriptions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "subscription not found");
        }
        return subscriptions.get(0);
    }

    public List<BillingOrderResponse> listOrders(String tenantCode) {
        String resolvedTenantCode = resolveTenantCode(tenantCode);
        return jdbcTemplate.query(
                "SELECT o.id, o.order_no, t.tenant_code, o.subscription_id, p.plan_code, p.plan_name, " +
                        "o.order_type, o.period_months, o.amount_cent, o.status, o.paid_time, o.create_time " +
                        "FROM platform_order o " +
                        "JOIN platform_tenant t ON o.tenant_id = t.id " +
                        "LEFT JOIN platform_plan p ON o.plan_id = p.id " +
                        "WHERE t.tenant_code = ? ORDER BY o.id DESC LIMIT 100",
                (rs, rowNum) -> mapOrder(rs.getLong("id"),
                        rs.getString("order_no"),
                        rs.getString("tenant_code"),
                        longOrNull(rs.getObject("subscription_id")),
                        rs.getString("plan_code"),
                        rs.getString("plan_name"),
                        rs.getString("order_type"),
                        rs.getInt("period_months"),
                        rs.getLong("amount_cent"),
                        rs.getString("status"),
                        rs.getTimestamp("paid_time"),
                        rs.getTimestamp("create_time")),
                resolvedTenantCode
            );
    }

    @Transactional
    @OperationLog(action = "BILLING_ORDER_CREATE", targetType = "platform_order")
    public BillingOrderResponse createOrder(BillingOrderCreateRequest request) {
        String tenantCode = resolveTenantCode(request.getTenantCode());
        TenantRecord tenant = tenantRecord(tenantCode);
        PlanRecord plan = planRecord(normalizePlanCode(request.getPlanCode()));
        String orderType = normalizeOrderType(request.getOrderType());
        int periodMonths = normalizePeriodMonths(request.getPeriodMonths(), plan.getBillingCycle());
        SubscriptionRecord subscription = latestSubscription(tenant.getId());
        Long amountCent = amountForPeriod(plan, periodMonths);
        jdbcTemplate.update(
                "INSERT INTO platform_order (order_no, tenant_id, subscription_id, plan_id, order_type, period_months, amount_cent, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, 'UNPAID')",
                orderNo(),
                tenant.getId(),
                subscription == null ? null : subscription.getId(),
                plan.getId(),
                orderType,
                periodMonths,
                amountCent);
        Long orderId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (amountCent == 0L) {
            confirmPayment(orderId, freePaymentRequest());
        }
        return getOrder(orderId);
    }

    @Transactional
    @OperationLog(action = "BILLING_PAYMENT_CONFIRM", targetType = "platform_order", targetIdArg = 0)
    public PaymentResponse confirmPayment(Long orderId, PaymentConfirmRequest request) {
        OrderRecord order = orderRecord(orderId);
        if ("PAID".equals(order.getStatus())) {
            return latestPayment(orderId);
        }
        Long paidAmount = request.getAmountCent() == null ? order.getAmountCent() : request.getAmountCent();
        if (!paidAmount.equals(order.getAmountCent())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payment amount does not match order amount");
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO platform_payment (order_id, payment_channel, payment_no, amount_cent, status, paid_time) " +
                        "VALUES (?, ?, ?, ?, 'SUCCESS', ?)",
                orderId,
                request.getPaymentChannel(),
                request.getPaymentNo(),
                paidAmount,
                now);
        Long paymentId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update(
                "UPDATE platform_order SET status = 'PAID', paid_time = ? WHERE id = ?",
                now,
                orderId);
        applyOrderToSubscription(order, now);
        return paymentResponse(paymentId);
    }

    public int expireSubscriptions() {
        int expiredTrials = jdbcTemplate.update(
                "UPDATE platform_subscription SET status = 'EXPIRED' " +
                        "WHERE status = 'TRIAL' AND trial_end_time < NOW()");
        int pastDueSubscriptions = jdbcTemplate.update(
                "UPDATE platform_subscription SET status = 'PAST_DUE' " +
                        "WHERE status = 'ACTIVE' AND end_time < NOW()");
        int frozenSubscriptions = jdbcTemplate.update(
                "UPDATE platform_subscription SET status = 'FROZEN' " +
                        "WHERE status = 'PAST_DUE' AND DATE_ADD(end_time, INTERVAL grace_days DAY) < NOW()");
        return expiredTrials + pastDueSubscriptions + frozenSubscriptions;
    }

    private void applyOrderToSubscription(OrderRecord order, LocalDateTime paidTime) {
        SubscriptionRecord subscription = order.getSubscriptionId() == null
                ? null
                : subscriptionRecord(order.getSubscriptionId());
        LocalDateTime baseTime = subscription == null || subscription.getEndTime().isBefore(paidTime)
                ? paidTime
                : subscription.getEndTime();
        LocalDateTime newEndTime = baseTime.plusMonths(order.getPeriodMonths());
        if (subscription == null) {
            jdbcTemplate.update(
                    "INSERT INTO platform_subscription (tenant_id, plan_id, status, start_time, end_time, trial_end_time, grace_days) " +
                            "VALUES (?, ?, 'ACTIVE', ?, ?, NULL, 7)",
                    order.getTenantId(),
                    order.getPlanId(),
                    paidTime,
                    newEndTime);
        } else {
            jdbcTemplate.update(
                    "UPDATE platform_subscription SET plan_id = ?, status = 'ACTIVE', end_time = ?, trial_end_time = NULL " +
                            "WHERE id = ?",
                    order.getPlanId(),
                    newEndTime,
                    subscription.getId());
        }
        jdbcTemplate.update("UPDATE platform_tenant SET status = 'ACTIVE' WHERE id = ?", order.getTenantId());
    }

    private BillingOrderResponse getOrder(Long orderId) {
        List<BillingOrderResponse> orders = jdbcTemplate.query(
                "SELECT o.id, o.order_no, t.tenant_code, o.subscription_id, p.plan_code, p.plan_name, " +
                        "o.order_type, o.period_months, o.amount_cent, o.status, o.paid_time, o.create_time " +
                        "FROM platform_order o " +
                        "JOIN platform_tenant t ON o.tenant_id = t.id " +
                        "LEFT JOIN platform_plan p ON o.plan_id = p.id " +
                        "WHERE o.id = ?",
                (rs, rowNum) -> mapOrder(rs.getLong("id"),
                        rs.getString("order_no"),
                        rs.getString("tenant_code"),
                        longOrNull(rs.getObject("subscription_id")),
                        rs.getString("plan_code"),
                        rs.getString("plan_name"),
                        rs.getString("order_type"),
                        rs.getInt("period_months"),
                        rs.getLong("amount_cent"),
                        rs.getString("status"),
                        rs.getTimestamp("paid_time"),
                        rs.getTimestamp("create_time")),
                orderId
            );
        if (orders.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found");
        }
        return orders.get(0);
    }

    private BillingOrderResponse mapOrder(Long id, String orderNo, String tenantCode, Long subscriptionId,
                                          String planCode, String planName, String orderType, Integer periodMonths,
                                          Long amountCent, String status, Timestamp paidTime, Timestamp createTime) {
        BillingOrderResponse response = new BillingOrderResponse();
        response.setId(id);
        response.setOrderNo(orderNo);
        response.setTenantCode(tenantCode);
        response.setSubscriptionId(subscriptionId);
        response.setPlanCode(planCode);
        response.setPlanName(planName);
        response.setOrderType(orderType);
        response.setPeriodMonths(periodMonths);
        response.setAmountCent(amountCent);
        response.setStatus(status);
        response.setPaidTime(toLocalDateTime(paidTime));
        response.setCreateTime(toLocalDateTime(createTime));
        return response;
    }

    private PaymentResponse latestPayment(Long orderId) {
        List<PaymentResponse> payments = jdbcTemplate.query(
                "SELECT id, order_id, payment_channel, payment_no, amount_cent, status, paid_time " +
                        "FROM platform_payment WHERE order_id = ? ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> mapPayment(rs.getLong("id"),
                        rs.getLong("order_id"),
                        rs.getString("payment_channel"),
                        rs.getString("payment_no"),
                        rs.getLong("amount_cent"),
                        rs.getString("status"),
                        rs.getTimestamp("paid_time")),
                orderId
            );
        if (payments.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "payment not found");
        }
        return payments.get(0);
    }

    private PaymentResponse paymentResponse(Long paymentId) {
        return jdbcTemplate.queryForObject(
                "SELECT id, order_id, payment_channel, payment_no, amount_cent, status, paid_time " +
                        "FROM platform_payment WHERE id = ?",
                (rs, rowNum) -> mapPayment(rs.getLong("id"),
                        rs.getLong("order_id"),
                        rs.getString("payment_channel"),
                        rs.getString("payment_no"),
                        rs.getLong("amount_cent"),
                        rs.getString("status"),
                        rs.getTimestamp("paid_time")),
                paymentId
            );
    }

    private PaymentResponse mapPayment(Long id, Long orderId, String paymentChannel, String paymentNo,
                                       Long amountCent, String status, Timestamp paidTime) {
        PaymentResponse response = new PaymentResponse();
        response.setId(id);
        response.setOrderId(orderId);
        response.setPaymentChannel(paymentChannel);
        response.setPaymentNo(paymentNo);
        response.setAmountCent(amountCent);
        response.setStatus(status);
        response.setPaidTime(toLocalDateTime(paidTime));
        return response;
    }

    private TenantRecord tenantRecord(String tenantCode) {
        List<TenantRecord> tenants = jdbcTemplate.query(
                "SELECT id, tenant_code FROM platform_tenant WHERE tenant_code = ?",
                (rs, rowNum) -> new TenantRecord(rs.getLong("id"), rs.getString("tenant_code")),
                tenantCode
            );
        if (tenants.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "tenant not found");
        }
        return tenants.get(0);
    }

    private PlanRecord planRecord(String planCode) {
        List<PlanRecord> plans = jdbcTemplate.query(
                "SELECT id, plan_code, plan_name, billing_cycle, price_cent FROM platform_plan " +
                        "WHERE plan_code = ? AND status = 'ACTIVE'",
                (rs, rowNum) -> new PlanRecord(rs.getLong("id"),
                        rs.getString("plan_code"),
                        rs.getString("plan_name"),
                        rs.getString("billing_cycle"),
                        rs.getLong("price_cent")),
                planCode
            );
        if (plans.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "plan not found");
        }
        return plans.get(0);
    }

    private SubscriptionRecord latestSubscription(Long tenantId) {
        List<SubscriptionRecord> subscriptions = jdbcTemplate.query(
                "SELECT id, tenant_id, plan_id, status, end_time FROM platform_subscription " +
                        "WHERE tenant_id = ? ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> new SubscriptionRecord(rs.getLong("id"),
                        rs.getLong("tenant_id"),
                        rs.getLong("plan_id"),
                        rs.getString("status"),
                        toLocalDateTime(rs.getTimestamp("end_time"))),
                tenantId
            );
        return subscriptions.isEmpty() ? null : subscriptions.get(0);
    }

    private SubscriptionRecord subscriptionRecord(Long subscriptionId) {
        List<SubscriptionRecord> subscriptions = jdbcTemplate.query(
                "SELECT id, tenant_id, plan_id, status, end_time FROM platform_subscription WHERE id = ?",
                (rs, rowNum) -> new SubscriptionRecord(rs.getLong("id"),
                        rs.getLong("tenant_id"),
                        rs.getLong("plan_id"),
                        rs.getString("status"),
                        toLocalDateTime(rs.getTimestamp("end_time"))),
                subscriptionId
            );
        if (subscriptions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "subscription not found");
        }
        return subscriptions.get(0);
    }

    private OrderRecord orderRecord(Long orderId) {
        List<OrderRecord> orders = jdbcTemplate.query(
                "SELECT id, tenant_id, subscription_id, plan_id, order_type, period_months, amount_cent, status " +
                        "FROM platform_order WHERE id = ?",
                (rs, rowNum) -> new OrderRecord(rs.getLong("id"),
                        rs.getLong("tenant_id"),
                        longOrNull(rs.getObject("subscription_id")),
                        rs.getLong("plan_id"),
                        rs.getString("order_type"),
                        rs.getInt("period_months"),
                        rs.getLong("amount_cent"),
                        rs.getString("status")),
                orderId
            );
        if (orders.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found");
        }
        return orders.get(0);
    }

    private String joinPlanIds(Map<Long, PlanResponse> plans) {
        StringBuilder builder = new StringBuilder();
        for (Long id : plans.keySet()) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(id);
        }
        return builder.toString();
    }

    private String resolveTenantCode(String tenantCode) {
        if (StringUtils.hasText(tenantCode)) {
            return normalizeTenantCode(tenantCode);
        }
        return normalizeTenantCode(TenantContext.getTenantCode());
    }

    private String normalizeTenantCode(String tenantCode) {
        if (!StringUtils.hasText(tenantCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantCode is required");
        }
        return tenantCode.trim().toLowerCase(Locale.ENGLISH);
    }

    private String normalizePlanCode(String planCode) {
        if (!StringUtils.hasText(planCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "planCode is required");
        }
        return planCode.trim().toLowerCase(Locale.ENGLISH);
    }

    private String normalizeOrderType(String orderType) {
        if (!StringUtils.hasText(orderType)) {
            return "RENEW";
        }
        String normalized = orderType.trim().toUpperCase(Locale.ENGLISH);
        if (!"RENEW".equals(normalized) && !"CHANGE_PLAN".equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported orderType");
        }
        return normalized;
    }

    private int normalizePeriodMonths(Integer periodMonths, String billingCycle) {
        if (periodMonths != null && periodMonths > 0) {
            return Math.min(periodMonths, 60);
        }
        return "YEAR".equalsIgnoreCase(billingCycle) ? 12 : 1;
    }

    private Long amountForPeriod(PlanRecord plan, int periodMonths) {
        if ("YEAR".equalsIgnoreCase(plan.getBillingCycle())) {
            long years = Math.max(1L, (periodMonths + 11L) / 12L);
            return plan.getPriceCent() * years;
        }
        return plan.getPriceCent() * periodMonths;
    }

    private String orderNo() {
        return "ORB" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) +
                UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ENGLISH);
    }

    private PaymentConfirmRequest freePaymentRequest() {
        PaymentConfirmRequest request = new PaymentConfirmRequest();
        request.setPaymentChannel("FREE");
        request.setPaymentNo("FREE");
        request.setAmountCent(0L);
        return request;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private Long longOrNull(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static class TenantRecord {
        private final Long id;
        private final String tenantCode;

        TenantRecord(Long id, String tenantCode) {
            this.id = id;
            this.tenantCode = tenantCode;
        }

        Long getId() {
            return id;
        }

        String getTenantCode() {
            return tenantCode;
        }
    }

    private static class PlanRecord {
        private final Long id;
        private final String planCode;
        private final String planName;
        private final String billingCycle;
        private final Long priceCent;

        PlanRecord(Long id, String planCode, String planName, String billingCycle, Long priceCent) {
            this.id = id;
            this.planCode = planCode;
            this.planName = planName;
            this.billingCycle = billingCycle;
            this.priceCent = priceCent;
        }

        Long getId() {
            return id;
        }

        String getPlanCode() {
            return planCode;
        }

        String getPlanName() {
            return planName;
        }

        String getBillingCycle() {
            return billingCycle;
        }

        Long getPriceCent() {
            return priceCent;
        }
    }

    private static class SubscriptionRecord {
        private final Long id;
        private final Long tenantId;
        private final Long planId;
        private final String status;
        private final LocalDateTime endTime;

        SubscriptionRecord(Long id, Long tenantId, Long planId, String status, LocalDateTime endTime) {
            this.id = id;
            this.tenantId = tenantId;
            this.planId = planId;
            this.status = status;
            this.endTime = endTime;
        }

        Long getId() {
            return id;
        }

        Long getTenantId() {
            return tenantId;
        }

        Long getPlanId() {
            return planId;
        }

        String getStatus() {
            return status;
        }

        LocalDateTime getEndTime() {
            return endTime;
        }
    }

    private static class OrderRecord {
        private final Long id;
        private final Long tenantId;
        private final Long subscriptionId;
        private final Long planId;
        private final String orderType;
        private final Integer periodMonths;
        private final Long amountCent;
        private final String status;

        OrderRecord(Long id, Long tenantId, Long subscriptionId, Long planId,
                    String orderType, Integer periodMonths, Long amountCent, String status) {
            this.id = id;
            this.tenantId = tenantId;
            this.subscriptionId = subscriptionId;
            this.planId = planId;
            this.orderType = orderType;
            this.periodMonths = periodMonths;
            this.amountCent = amountCent;
            this.status = status;
        }

        Long getId() {
            return id;
        }

        Long getTenantId() {
            return tenantId;
        }

        Long getSubscriptionId() {
            return subscriptionId;
        }

        Long getPlanId() {
            return planId;
        }

        String getOrderType() {
            return orderType;
        }

        Integer getPeriodMonths() {
            return periodMonths;
        }

        Long getAmountCent() {
            return amountCent;
        }

        String getStatus() {
            return status;
        }
    }
}
