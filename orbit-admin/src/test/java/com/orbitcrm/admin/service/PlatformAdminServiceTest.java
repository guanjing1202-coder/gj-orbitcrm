package com.orbitcrm.admin.service;

import com.orbitcrm.admin.api.PlatformOrderResponse;
import com.orbitcrm.admin.api.PlatformSubscriptionResponse;
import com.orbitcrm.admin.api.PlatformTenantResponse;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformAdminServiceTest {
    private static final Long TENANT_ID = 10001L;
    private static final Long SUBSCRIPTION_ID = 77L;
    private static final Long ORDER_ID = 88L;

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getTenantReturnsLatestSubscriptionSummary() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformAdminService service = new PlatformAdminService(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any()))
                .thenAnswer(this::mapSingleTenantQuery);

        PlatformTenantResponse response = service.getTenant(TENANT_ID);

        assertEquals(TENANT_ID, response.getId());
        assertEquals("demo-company", response.getTenantCode());
        assertEquals("professional", response.getPlanCode());
        assertEquals("ACTIVE", response.getSubscriptionStatus());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listTenantOrdersChecksTenantBeforeQueryingOrders() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformAdminService service = new PlatformAdminService(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any()))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    if (sql.contains("FROM platform_tenant t")) {
                        return mapSingleTenantQuery(invocation);
                    }
                    if (sql.contains("FROM platform_order o")) {
                        return Collections.singletonList(mapOrder(invocation.getArgument(1)));
                    }
                    return Collections.emptyList();
                });

        List<PlatformOrderResponse> response = service.listTenantOrders(TENANT_ID);

        assertEquals(1, response.size());
        assertEquals(ORDER_ID, response.get(0).getId());
        assertEquals("PAID", response.get(0).getStatus());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getSubscriptionReturnsSubscriptionDetail() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformAdminService service = new PlatformAdminService(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any()))
                .thenAnswer(invocation -> Collections.singletonList(mapSubscription(invocation.getArgument(1))));

        PlatformSubscriptionResponse response = service.getSubscription(SUBSCRIPTION_ID);

        assertEquals(SUBSCRIPTION_ID, response.getId());
        assertEquals("demo-company", response.getTenantCode());
        assertEquals("ACTIVE", response.getStatus());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getOrderReturnsOrderDetail() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformAdminService service = new PlatformAdminService(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any()))
                .thenAnswer(invocation -> Collections.singletonList(mapOrder(invocation.getArgument(1))));

        PlatformOrderResponse response = service.getOrder(ORDER_ID);

        assertEquals(ORDER_ID, response.getId());
        assertEquals("ORB202605210001", response.getOrderNo());
        assertEquals("PAID", response.getStatus());
    }

    private Object mapSingleTenantQuery(InvocationOnMock invocation) throws Exception {
        return Collections.singletonList(mapTenant(invocation.getArgument(1)));
    }

    private PlatformTenantResponse mapTenant(RowMapper<PlatformTenantResponse> mapper) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(TENANT_ID);
        when(resultSet.getString("tenant_code")).thenReturn("demo-company");
        when(resultSet.getString("tenant_name")).thenReturn("Demo Company");
        when(resultSet.getString("status")).thenReturn("ACTIVE");
        when(resultSet.getString("contact_name")).thenReturn("Alice");
        when(resultSet.getString("contact_email")).thenReturn("alice@example.com");
        when(resultSet.getString("plan_code")).thenReturn("professional");
        when(resultSet.getString("subscription_status")).thenReturn("ACTIVE");
        when(resultSet.getTimestamp("subscription_end_time"))
                .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 6, 21, 9, 30)));
        when(resultSet.getTimestamp("create_time"))
                .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 5, 21, 9, 30)));
        return mapper.mapRow(resultSet, 0);
    }

    private PlatformSubscriptionResponse mapSubscription(RowMapper<PlatformSubscriptionResponse> mapper) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(SUBSCRIPTION_ID);
        when(resultSet.getString("tenant_code")).thenReturn("demo-company");
        when(resultSet.getString("plan_code")).thenReturn("professional");
        when(resultSet.getString("status")).thenReturn("ACTIVE");
        when(resultSet.getTimestamp("start_time"))
                .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 5, 21, 9, 30)));
        when(resultSet.getTimestamp("end_time"))
                .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 6, 21, 9, 30)));
        when(resultSet.getTimestamp("trial_end_time")).thenReturn(null);
        when(resultSet.getInt("grace_days")).thenReturn(7);
        return mapper.mapRow(resultSet, 0);
    }

    private PlatformOrderResponse mapOrder(RowMapper<PlatformOrderResponse> mapper) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(ORDER_ID);
        when(resultSet.getString("order_no")).thenReturn("ORB202605210001");
        when(resultSet.getString("tenant_code")).thenReturn("demo-company");
        when(resultSet.getString("plan_code")).thenReturn("professional");
        when(resultSet.getString("order_type")).thenReturn("RENEW");
        when(resultSet.getInt("period_months")).thenReturn(1);
        when(resultSet.getLong("amount_cent")).thenReturn(9900L);
        when(resultSet.getString("status")).thenReturn("PAID");
        when(resultSet.getTimestamp("paid_time"))
                .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 5, 21, 10, 30)));
        when(resultSet.getTimestamp("create_time"))
                .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 5, 21, 9, 30)));
        return mapper.mapRow(resultSet, 0);
    }
}
