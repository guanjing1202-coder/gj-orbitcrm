package com.orbitcrm.admin.service;

import com.orbitcrm.admin.api.PlatformDashboardResponse;
import com.orbitcrm.admin.api.PlatformOperationLogResponse;
import com.orbitcrm.admin.api.PlatformOrderResponse;
import com.orbitcrm.admin.api.PlatformPlanFeatureRequest;
import com.orbitcrm.admin.api.PlatformPlanResponse;
import com.orbitcrm.admin.api.PlatformPlanUpsertRequest;
import com.orbitcrm.admin.api.PlatformSubscriptionResponse;
import com.orbitcrm.admin.api.PlatformTenantResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PlatformAdminService {
    private final JdbcTemplate jdbcTemplate;

    public PlatformAdminService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PlatformDashboardResponse dashboard() {
        PlatformDashboardResponse response = new PlatformDashboardResponse();
        response.setTenantCount(count("SELECT COUNT(1) FROM platform_tenant"));
        response.setActiveTenantCount(count("SELECT COUNT(1) FROM platform_tenant WHERE status = 'ACTIVE'"));
        response.setTrialSubscriptionCount(count("SELECT COUNT(1) FROM platform_subscription WHERE status = 'TRIAL'"));
        response.setActiveSubscriptionCount(count("SELECT COUNT(1) FROM platform_subscription WHERE status = 'ACTIVE'"));
        response.setPastDueSubscriptionCount(count("SELECT COUNT(1) FROM platform_subscription WHERE status = 'PAST_DUE'"));
        response.setFrozenSubscriptionCount(count("SELECT COUNT(1) FROM platform_subscription WHERE status = 'FROZEN'"));
        response.setMonthlyRecurringRevenueCent(sum(
                "SELECT COALESCE(SUM(CASE WHEN p.billing_cycle = 'YEAR' THEN p.price_cent / 12 ELSE p.price_cent END), 0) " +
                        "FROM platform_subscription s JOIN platform_plan p ON s.plan_id = p.id " +
                        "WHERE s.status IN ('TRIAL', 'ACTIVE')"));
        response.setCurrentMonthPaidAmountCent(sum(
                "SELECT COALESCE(SUM(amount_cent), 0) FROM platform_order " +
                        "WHERE status = 'PAID' AND paid_time >= DATE_FORMAT(NOW(), '%Y-%m-01')"));
        return response;
    }

    public List<PlatformTenantResponse> listTenants(String status) {
        if (StringUtils.hasText(status)) {
            return jdbcTemplate.query(
                    tenantListSql("WHERE t.status = ?"),
                    (rs, rowNum) -> mapTenant(rs.getLong("id"),
                            rs.getString("tenant_code"),
                            rs.getString("tenant_name"),
                            rs.getString("status"),
                            rs.getString("contact_name"),
                            rs.getString("contact_email"),
                            rs.getString("plan_code"),
                            rs.getString("subscription_status"),
                            rs.getTimestamp("subscription_end_time"),
                            rs.getTimestamp("create_time")),
                    normalizeStatus(status)
                );
        }
        return jdbcTemplate.query(
                tenantListSql(""),
                (rs, rowNum) -> mapTenant(rs.getLong("id"),
                        rs.getString("tenant_code"),
                        rs.getString("tenant_name"),
                        rs.getString("status"),
                        rs.getString("contact_name"),
                        rs.getString("contact_email"),
                        rs.getString("plan_code"),
                        rs.getString("subscription_status"),
                        rs.getTimestamp("subscription_end_time"),
                        rs.getTimestamp("create_time")));
    }

    public PlatformTenantResponse updateTenantStatus(Long id, String status) {
        String normalizedStatus = normalizeStatus(status);
        int updated = jdbcTemplate.update("UPDATE platform_tenant SET status = ? WHERE id = ?", normalizedStatus, id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "tenant not found");
        }
        writePlatformLog("TENANT_STATUS_UPDATE", "platform_tenant", String.valueOf(id),
                "{\"status\":\"" + normalizedStatus + "\"}");
        return getTenant(id);
    }

    public List<PlatformPlanResponse> listPlans() {
        Map<Long, PlatformPlanResponse> plans = new LinkedHashMap<Long, PlatformPlanResponse>();
        jdbcTemplate.query(
                "SELECT id, plan_code, plan_name, billing_cycle, price_cent, status FROM platform_plan ORDER BY id",
                rs -> {
                    PlatformPlanResponse plan = new PlatformPlanResponse();
                    plan.setId(rs.getLong("id"));
                    plan.setPlanCode(rs.getString("plan_code"));
                    plan.setPlanName(rs.getString("plan_name"));
                    plan.setBillingCycle(rs.getString("billing_cycle"));
                    plan.setPriceCent(rs.getLong("price_cent"));
                    plan.setStatus(rs.getString("status"));
                    plans.put(plan.getId(), plan);
                });
        if (plans.isEmpty()) {
            return new ArrayList<PlatformPlanResponse>();
        }
        jdbcTemplate.query(
                "SELECT plan_id, feature_key, feature_value, value_type FROM platform_plan_feature " +
                        "WHERE plan_id IN (" + joinIds(plans) + ") ORDER BY plan_id, feature_key",
                rs -> {
                    PlatformPlanResponse plan = plans.get(rs.getLong("plan_id"));
                    if (plan != null) {
                        PlatformPlanFeatureRequest feature = new PlatformPlanFeatureRequest();
                        feature.setFeatureKey(rs.getString("feature_key"));
                        feature.setFeatureValue(rs.getString("feature_value"));
                        feature.setValueType(rs.getString("value_type"));
                        plan.getFeatures().add(feature);
                    }
                });
        return new ArrayList<PlatformPlanResponse>(plans.values());
    }

    @Transactional
    public PlatformPlanResponse upsertPlan(PlatformPlanUpsertRequest request) {
        String planCode = normalizeCode(request.getPlanCode());
        String billingCycle = normalizeBillingCycle(request.getBillingCycle());
        String status = normalizePlanStatus(request.getStatus());
        jdbcTemplate.update(
                "INSERT INTO platform_plan (plan_code, plan_name, billing_cycle, price_cent, status) " +
                        "VALUES (?, ?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE plan_name = VALUES(plan_name), billing_cycle = VALUES(billing_cycle), " +
                        "price_cent = VALUES(price_cent), status = VALUES(status)",
                planCode,
                request.getPlanName(),
                billingCycle,
                request.getPriceCent(),
                status);
        Long planId = jdbcTemplate.queryForObject(
                "SELECT id FROM platform_plan WHERE plan_code = ?",
                Long.class,
                planCode
            );
        if (request.getFeatures() != null) {
            for (PlatformPlanFeatureRequest feature : request.getFeatures()) {
                upsertPlanFeature(planId, feature);
            }
        }
        writePlatformLog("PLAN_UPSERT", "platform_plan", String.valueOf(planId),
                "{\"planCode\":\"" + planCode + "\"}");
        return getPlan(planId);
    }

    public List<PlatformSubscriptionResponse> listSubscriptions(String status) {
        if (StringUtils.hasText(status)) {
            return jdbcTemplate.query(
                    subscriptionListSql("WHERE s.status = ?"),
                    (rs, rowNum) -> mapSubscription(rs.getLong("id"),
                            rs.getString("tenant_code"),
                            rs.getString("plan_code"),
                            rs.getString("status"),
                            rs.getTimestamp("start_time"),
                            rs.getTimestamp("end_time"),
                            rs.getTimestamp("trial_end_time"),
                            rs.getInt("grace_days")),
                    normalizeStatus(status)
                );
        }
        return jdbcTemplate.query(
                subscriptionListSql(""),
                (rs, rowNum) -> mapSubscription(rs.getLong("id"),
                        rs.getString("tenant_code"),
                        rs.getString("plan_code"),
                        rs.getString("status"),
                        rs.getTimestamp("start_time"),
                        rs.getTimestamp("end_time"),
                        rs.getTimestamp("trial_end_time"),
                        rs.getInt("grace_days")));
    }

    public List<PlatformOrderResponse> listOrders(String status) {
        if (StringUtils.hasText(status)) {
            return jdbcTemplate.query(
                    orderListSql("WHERE o.status = ?"),
                    (rs, rowNum) -> mapOrder(rs.getLong("id"),
                            rs.getString("order_no"),
                            rs.getString("tenant_code"),
                            rs.getString("plan_code"),
                            rs.getString("order_type"),
                            rs.getInt("period_months"),
                            rs.getLong("amount_cent"),
                            rs.getString("status"),
                            rs.getTimestamp("paid_time"),
                            rs.getTimestamp("create_time")),
                    normalizeStatus(status)
                );
        }
        return jdbcTemplate.query(
                orderListSql(""),
                (rs, rowNum) -> mapOrder(rs.getLong("id"),
                        rs.getString("order_no"),
                        rs.getString("tenant_code"),
                        rs.getString("plan_code"),
                        rs.getString("order_type"),
                        rs.getInt("period_months"),
                        rs.getLong("amount_cent"),
                        rs.getString("status"),
                        rs.getTimestamp("paid_time"),
                        rs.getTimestamp("create_time")));
    }

    public List<PlatformOperationLogResponse> listOperationLogs(String action, Integer limit) {
        int safeLimit = limit == null ? 100 : Math.max(1, Math.min(limit, 500));
        if (StringUtils.hasText(action)) {
            return jdbcTemplate.query(
                    "SELECT id, operator_id, action, target_type, target_id, detail_json, ip, create_time " +
                            "FROM platform_operation_log WHERE action = ? ORDER BY id DESC LIMIT " + safeLimit,
                    (rs, rowNum) -> mapLog(rs.getLong("id"),
                            longOrNull(rs.getObject("operator_id")),
                            rs.getString("action"),
                            rs.getString("target_type"),
                            rs.getString("target_id"),
                            rs.getString("detail_json"),
                            rs.getString("ip"),
                            rs.getTimestamp("create_time")),
                    action
                );
        }
        return jdbcTemplate.query(
                "SELECT id, operator_id, action, target_type, target_id, detail_json, ip, create_time " +
                        "FROM platform_operation_log ORDER BY id DESC LIMIT " + safeLimit,
                (rs, rowNum) -> mapLog(rs.getLong("id"),
                        longOrNull(rs.getObject("operator_id")),
                        rs.getString("action"),
                        rs.getString("target_type"),
                        rs.getString("target_id"),
                        rs.getString("detail_json"),
                        rs.getString("ip"),
                        rs.getTimestamp("create_time")));
    }

    private PlatformTenantResponse getTenant(Long id) {
        List<PlatformTenantResponse> tenants = jdbcTemplate.query(
                tenantListSql("WHERE t.id = ?"),
                (rs, rowNum) -> mapTenant(rs.getLong("id"),
                        rs.getString("tenant_code"),
                        rs.getString("tenant_name"),
                        rs.getString("status"),
                        rs.getString("contact_name"),
                        rs.getString("contact_email"),
                        rs.getString("plan_code"),
                        rs.getString("subscription_status"),
                        rs.getTimestamp("subscription_end_time"),
                        rs.getTimestamp("create_time")),
                id
            );
        if (tenants.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "tenant not found");
        }
        return tenants.get(0);
    }

    private PlatformPlanResponse getPlan(Long id) {
        for (PlatformPlanResponse plan : listPlans()) {
            if (id.equals(plan.getId())) {
                return plan;
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "plan not found");
    }

    private void upsertPlanFeature(Long planId, PlatformPlanFeatureRequest feature) {
        jdbcTemplate.update(
                "INSERT INTO platform_plan_feature (plan_id, feature_key, feature_value, value_type) " +
                        "VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE feature_value = VALUES(feature_value), value_type = VALUES(value_type)",
                planId,
                normalizeFeatureKey(feature.getFeatureKey()),
                feature.getFeatureValue(),
                StringUtils.hasText(feature.getValueType()) ? feature.getValueType().trim().toUpperCase(Locale.ENGLISH) : "NUMBER");
    }

    private String tenantListSql(String whereClause) {
        return "SELECT t.id, t.tenant_code, t.tenant_name, t.status, t.contact_name, t.contact_email, " +
                "p.plan_code, s.status AS subscription_status, s.end_time AS subscription_end_time, t.create_time " +
                "FROM platform_tenant t " +
                "LEFT JOIN platform_subscription s ON s.id = (" +
                "  SELECT s2.id FROM platform_subscription s2 WHERE s2.tenant_id = t.id ORDER BY s2.id DESC LIMIT 1" +
                ") " +
                "LEFT JOIN platform_plan p ON s.plan_id = p.id " +
                whereClause + " ORDER BY t.id DESC LIMIT 200";
    }

    private String subscriptionListSql(String whereClause) {
        return "SELECT s.id, t.tenant_code, p.plan_code, s.status, s.start_time, s.end_time, s.trial_end_time, s.grace_days " +
                "FROM platform_subscription s " +
                "JOIN platform_tenant t ON s.tenant_id = t.id " +
                "JOIN platform_plan p ON s.plan_id = p.id " +
                whereClause + " ORDER BY s.id DESC LIMIT 200";
    }

    private String orderListSql(String whereClause) {
        return "SELECT o.id, o.order_no, t.tenant_code, p.plan_code, o.order_type, o.period_months, " +
                "o.amount_cent, o.status, o.paid_time, o.create_time " +
                "FROM platform_order o " +
                "JOIN platform_tenant t ON o.tenant_id = t.id " +
                "LEFT JOIN platform_plan p ON o.plan_id = p.id " +
                whereClause + " ORDER BY o.id DESC LIMIT 200";
    }

    private PlatformTenantResponse mapTenant(Long id, String tenantCode, String tenantName, String status,
                                             String contactName, String contactEmail, String planCode,
                                             String subscriptionStatus, Timestamp subscriptionEndTime,
                                             Timestamp createTime) {
        PlatformTenantResponse response = new PlatformTenantResponse();
        response.setId(id);
        response.setTenantCode(tenantCode);
        response.setTenantName(tenantName);
        response.setStatus(status);
        response.setContactName(contactName);
        response.setContactEmail(contactEmail);
        response.setPlanCode(planCode);
        response.setSubscriptionStatus(subscriptionStatus);
        response.setSubscriptionEndTime(toLocalDateTime(subscriptionEndTime));
        response.setCreateTime(toLocalDateTime(createTime));
        return response;
    }

    private PlatformSubscriptionResponse mapSubscription(Long id, String tenantCode, String planCode, String status,
                                                         Timestamp startTime, Timestamp endTime,
                                                         Timestamp trialEndTime, Integer graceDays) {
        PlatformSubscriptionResponse response = new PlatformSubscriptionResponse();
        response.setId(id);
        response.setTenantCode(tenantCode);
        response.setPlanCode(planCode);
        response.setStatus(status);
        response.setStartTime(toLocalDateTime(startTime));
        response.setEndTime(toLocalDateTime(endTime));
        response.setTrialEndTime(toLocalDateTime(trialEndTime));
        response.setGraceDays(graceDays);
        return response;
    }

    private PlatformOrderResponse mapOrder(Long id, String orderNo, String tenantCode, String planCode,
                                           String orderType, Integer periodMonths, Long amountCent,
                                           String status, Timestamp paidTime, Timestamp createTime) {
        PlatformOrderResponse response = new PlatformOrderResponse();
        response.setId(id);
        response.setOrderNo(orderNo);
        response.setTenantCode(tenantCode);
        response.setPlanCode(planCode);
        response.setOrderType(orderType);
        response.setPeriodMonths(periodMonths);
        response.setAmountCent(amountCent);
        response.setStatus(status);
        response.setPaidTime(toLocalDateTime(paidTime));
        response.setCreateTime(toLocalDateTime(createTime));
        return response;
    }

    private PlatformOperationLogResponse mapLog(Long id, Long operatorId, String action, String targetType,
                                                String targetId, String detailJson, String ip, Timestamp createTime) {
        PlatformOperationLogResponse response = new PlatformOperationLogResponse();
        response.setId(id);
        response.setOperatorId(operatorId);
        response.setAction(action);
        response.setTargetType(targetType);
        response.setTargetId(targetId);
        response.setDetailJson(detailJson);
        response.setIp(ip);
        response.setCreateTime(toLocalDateTime(createTime));
        return response;
    }

    private void writePlatformLog(String action, String targetType, String targetId, String detailJson) {
        jdbcTemplate.update(
                "INSERT INTO platform_operation_log (action, target_type, target_id, detail_json) VALUES (?, ?, ?, ?)",
                action,
                targetType,
                targetId,
                detailJson);
    }

    private Integer count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private Long sum(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private String joinIds(Map<Long, PlatformPlanResponse> plans) {
        StringBuilder builder = new StringBuilder();
        for (Long id : plans.keySet()) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(id);
        }
        return builder.toString();
    }

    private String normalizeCode(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code is required");
        }
        return value.trim().toLowerCase(Locale.ENGLISH);
    }

    private String normalizeFeatureKey(String value) {
        return normalizeCode(value).replace("-", "_");
    }

    private String normalizeBillingCycle(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ENGLISH) : "";
        if (!"MONTH".equals(normalized) && !"YEAR".equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "billingCycle must be MONTH or YEAR");
        }
        return normalized;
    }

    private String normalizeStatus(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }
        return value.trim().toUpperCase(Locale.ENGLISH);
    }

    private String normalizePlanStatus(String value) {
        String normalized = normalizeStatus(value);
        if (!"ACTIVE".equals(normalized) && !"DISABLED".equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "plan status must be ACTIVE or DISABLED");
        }
        return normalized;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private Long longOrNull(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
