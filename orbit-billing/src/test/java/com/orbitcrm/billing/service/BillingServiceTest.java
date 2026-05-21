package com.orbitcrm.billing.service;

import com.orbitcrm.billing.api.BillingOrderResponse;
import com.orbitcrm.billing.api.PaymentConfirmRequest;
import com.orbitcrm.billing.api.SubscriptionResponse;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillingServiceTest {
    private static final String TENANT_CODE = "demo-company";
    private static final Long TENANT_ID = 10001L;
    private static final Long SUBSCRIPTION_ID = 77L;
    private static final Long ORDER_ID = 88L;

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void cancelOrderCancelsOnlyUnpaidOrder() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        BillingService billingService = new BillingService(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any()))
                .thenAnswer(invocation -> orderQuery(invocation, "UNPAID", "CANCELED"));
        when(jdbcTemplate.update(
                "UPDATE platform_order SET status = 'CANCELED' WHERE id = ? AND status = 'UNPAID'",
                ORDER_ID)).thenReturn(1);

        BillingOrderResponse response = billingService.cancelOrder(ORDER_ID);

        assertEquals("CANCELED", response.getStatus());
        verify(jdbcTemplate).update(
                "UPDATE platform_order SET status = 'CANCELED' WHERE id = ? AND status = 'UNPAID'",
                ORDER_ID);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void confirmPaymentRejectsCanceledOrder() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        BillingService billingService = new BillingService(jdbcTemplate);
        PaymentConfirmRequest request = new PaymentConfirmRequest();
        request.setPaymentChannel("MANUAL");
        request.setPaymentNo("PAY-001");
        request.setAmountCent(9900L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any()))
                .thenAnswer(invocation -> orderQuery(invocation, "CANCELED", "CANCELED"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> billingService.confirmPayment(ORDER_ID, request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void cancelCurrentSubscriptionMarksSubscriptionCanceled() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        BillingService billingService = new BillingService(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any()))
                .thenAnswer(invocation -> subscriptionQuery(invocation, "ACTIVE", "CANCELED"));
        when(jdbcTemplate.update(
                "UPDATE platform_subscription SET status = 'CANCELED' " +
                        "WHERE id = ? AND status IN ('TRIAL', 'ACTIVE', 'PAST_DUE')",
                SUBSCRIPTION_ID)).thenReturn(1);

        SubscriptionResponse response = billingService.cancelCurrentSubscription(TENANT_CODE);

        assertEquals("CANCELED", response.getStatus());
        verify(jdbcTemplate).update(
                "UPDATE platform_subscription SET status = 'CANCELED' " +
                        "WHERE id = ? AND status IN ('TRIAL', 'ACTIVE', 'PAST_DUE')",
                SUBSCRIPTION_ID);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void resumeCurrentSubscriptionReactivatesCanceledSubscriptionBeforeExpiry() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        BillingService billingService = new BillingService(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any()))
                .thenAnswer(invocation -> subscriptionQuery(invocation, "CANCELED", "ACTIVE"));
        when(jdbcTemplate.update(
                "UPDATE platform_subscription SET status = 'ACTIVE', trial_end_time = NULL " +
                        "WHERE id = ? AND status = 'CANCELED' AND end_time >= NOW()",
                SUBSCRIPTION_ID)).thenReturn(1);

        SubscriptionResponse response = billingService.resumeCurrentSubscription(TENANT_CODE);

        assertEquals("ACTIVE", response.getStatus());
        verify(jdbcTemplate).update(
                "UPDATE platform_subscription SET status = 'ACTIVE', trial_end_time = NULL " +
                        "WHERE id = ? AND status = 'CANCELED' AND end_time >= NOW()",
                SUBSCRIPTION_ID);
    }

    private Object orderQuery(InvocationOnMock invocation,
                              String recordStatus,
                              String responseStatus) throws Exception {
        String sql = invocation.getArgument(0);
        RowMapper mapper = invocation.getArgument(1);
        if (sql.contains("FROM platform_order WHERE id = ?")) {
            return Collections.singletonList(mapOrderRecord(mapper, recordStatus));
        }
        if (sql.contains("JOIN platform_tenant t ON o.tenant_id = t.id")) {
            return Collections.singletonList(mapOrderResponse(mapper, responseStatus));
        }
        return Collections.emptyList();
    }

    private Object subscriptionQuery(InvocationOnMock invocation,
                                     String recordStatus,
                                     String responseStatus) throws Exception {
        String sql = invocation.getArgument(0);
        RowMapper mapper = invocation.getArgument(1);
        if (sql.contains("FROM platform_tenant WHERE tenant_code = ?")) {
            return Collections.singletonList(mapTenant(mapper));
        }
        if (sql.contains("SELECT id, tenant_id, plan_id, status, end_time FROM platform_subscription")) {
            return Collections.singletonList(mapSubscriptionRecord(mapper, recordStatus));
        }
        if (sql.contains("FROM platform_subscription s")) {
            return Collections.singletonList(mapSubscriptionResponse(mapper, responseStatus));
        }
        return Collections.emptyList();
    }

    private Object mapTenant(RowMapper mapper) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(TENANT_ID);
        when(resultSet.getString("tenant_code")).thenReturn(TENANT_CODE);
        return mapper.mapRow(resultSet, 0);
    }

    private Object mapSubscriptionRecord(RowMapper mapper, String status) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(SUBSCRIPTION_ID);
        when(resultSet.getLong("tenant_id")).thenReturn(TENANT_ID);
        when(resultSet.getLong("plan_id")).thenReturn(3L);
        when(resultSet.getString("status")).thenReturn(status);
        when(resultSet.getTimestamp("end_time"))
                .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 6, 21, 9, 30)));
        return mapper.mapRow(resultSet, 0);
    }

    private Object mapSubscriptionResponse(RowMapper mapper, String status) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(SUBSCRIPTION_ID);
        when(resultSet.getString("tenant_code")).thenReturn(TENANT_CODE);
        when(resultSet.getString("plan_code")).thenReturn("professional");
        when(resultSet.getString("plan_name")).thenReturn("Professional");
        when(resultSet.getString("status")).thenReturn(status);
        when(resultSet.getTimestamp("start_time"))
                .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 5, 21, 9, 30)));
        when(resultSet.getTimestamp("end_time"))
                .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 6, 21, 9, 30)));
        when(resultSet.getTimestamp("trial_end_time")).thenReturn(null);
        when(resultSet.getInt("grace_days")).thenReturn(7);
        return mapper.mapRow(resultSet, 0);
    }

    private Object mapOrderRecord(RowMapper mapper, String status) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(ORDER_ID);
        when(resultSet.getLong("tenant_id")).thenReturn(TENANT_ID);
        when(resultSet.getObject("subscription_id")).thenReturn(SUBSCRIPTION_ID);
        when(resultSet.getLong("plan_id")).thenReturn(3L);
        when(resultSet.getString("order_type")).thenReturn("RENEW");
        when(resultSet.getInt("period_months")).thenReturn(1);
        when(resultSet.getLong("amount_cent")).thenReturn(9900L);
        when(resultSet.getString("status")).thenReturn(status);
        return mapper.mapRow(resultSet, 0);
    }

    private Object mapOrderResponse(RowMapper mapper, String status) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(ORDER_ID);
        when(resultSet.getString("order_no")).thenReturn("ORB202605210001");
        when(resultSet.getString("tenant_code")).thenReturn(TENANT_CODE);
        when(resultSet.getObject("subscription_id")).thenReturn(SUBSCRIPTION_ID);
        when(resultSet.getString("plan_code")).thenReturn("professional");
        when(resultSet.getString("plan_name")).thenReturn("Professional");
        when(resultSet.getString("order_type")).thenReturn("RENEW");
        when(resultSet.getInt("period_months")).thenReturn(1);
        when(resultSet.getLong("amount_cent")).thenReturn(9900L);
        when(resultSet.getString("status")).thenReturn(status);
        when(resultSet.getTimestamp("paid_time")).thenReturn(null);
        when(resultSet.getTimestamp("create_time"))
                .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 5, 21, 9, 30)));
        return mapper.mapRow(resultSet, 0);
    }
}
