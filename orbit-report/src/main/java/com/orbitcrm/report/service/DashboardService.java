package com.orbitcrm.report.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.report.api.DashboardSummaryResponse;
import com.orbitcrm.report.api.DealFunnelStageResponse;
import com.orbitcrm.report.api.NameValueResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DashboardService {
    private final TenantJdbcTemplateProvider tenantJdbcTemplateProvider;

    public DashboardService(TenantJdbcTemplateProvider tenantJdbcTemplateProvider) {
        this.tenantJdbcTemplateProvider = tenantJdbcTemplateProvider;
    }

    public DashboardSummaryResponse summary() {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        DashboardSummaryResponse response = new DashboardSummaryResponse();
        response.setLeadCount(count(jdbcTemplate, "SELECT COUNT(1) FROM crm_lead WHERE status <> 'DELETED'"));
        response.setCustomerCount(count(jdbcTemplate, "SELECT COUNT(1) FROM crm_customer WHERE status <> 'DELETED'"));
        response.setOpenDealCount(count(jdbcTemplate, "SELECT COUNT(1) FROM crm_deal WHERE status = 'OPEN'"));
        response.setOpenDealAmountCent(sum(jdbcTemplate, "SELECT COALESCE(SUM(amount_cent), 0) FROM crm_deal WHERE status = 'OPEN'"));
        response.setTodayTaskCount(count(jdbcTemplate,
                "SELECT COUNT(1) FROM crm_task WHERE status = 'TODO' AND DATE(due_time) = CURDATE()"));
        response.setOverdueTaskCount(count(jdbcTemplate,
                "SELECT COUNT(1) FROM crm_task WHERE status = 'TODO' AND due_time < NOW()"));
        response.setLeadStatus(leadStatus(jdbcTemplate));
        response.setDealStatus(dealStatus(jdbcTemplate));
        response.setTaskStatus(taskStatus(jdbcTemplate));
        response.setDealFunnel(dealFunnelAmount(jdbcTemplate));
        return response;
    }

    public List<NameValueResponse> leadStatus() {
        return leadStatus(tenantJdbcTemplateProvider.currentTenantJdbcTemplate());
    }

    public List<NameValueResponse> dealStatus() {
        return dealStatus(tenantJdbcTemplateProvider.currentTenantJdbcTemplate());
    }

    public List<NameValueResponse> taskStatus() {
        return taskStatus(tenantJdbcTemplateProvider.currentTenantJdbcTemplate());
    }

    public List<DealFunnelStageResponse> dealFunnel() {
        return dealFunnel(tenantJdbcTemplateProvider.currentTenantJdbcTemplate());
    }

    private List<NameValueResponse> leadStatus(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.query(
                "SELECT status, COUNT(1) AS total FROM crm_lead WHERE status <> 'DELETED' GROUP BY status ORDER BY status",
                (rs, rowNum) -> new NameValueResponse(rs.getString("status"), rs.getLong("total")));
    }

    private List<NameValueResponse> dealStatus(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.query(
                "SELECT status, COUNT(1) AS total FROM crm_deal WHERE status <> 'DELETED' GROUP BY status ORDER BY status",
                (rs, rowNum) -> new NameValueResponse(rs.getString("status"), rs.getLong("total")));
    }

    private List<NameValueResponse> taskStatus(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.query(
                "SELECT status, COUNT(1) AS total FROM crm_task WHERE status <> 'DELETED' GROUP BY status ORDER BY status",
                (rs, rowNum) -> new NameValueResponse(rs.getString("status"), rs.getLong("total")));
    }

    private List<NameValueResponse> dealFunnelAmount(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.query(
                "SELECT s.stage_name, COALESCE(SUM(d.amount_cent), 0) AS total_amount " +
                        "FROM crm_pipeline_stage s " +
                        "LEFT JOIN crm_deal d ON s.id = d.stage_id AND d.status = 'OPEN' " +
                        "GROUP BY s.id, s.stage_name, s.sort_order ORDER BY s.sort_order, s.id",
                (rs, rowNum) -> new NameValueResponse(rs.getString("stage_name"), rs.getLong("total_amount")));
    }

    private List<DealFunnelStageResponse> dealFunnel(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.query(
                "SELECT s.id, s.stage_name, s.win_probability, s.sort_order, COUNT(d.id) AS deal_count, " +
                        "COALESCE(SUM(d.amount_cent), 0) AS total_amount " +
                        "FROM crm_pipeline_stage s " +
                        "LEFT JOIN crm_deal d ON s.id = d.stage_id AND d.status = 'OPEN' " +
                        "GROUP BY s.id, s.stage_name, s.win_probability, s.sort_order ORDER BY s.sort_order, s.id",
                (rs, rowNum) -> {
                    DealFunnelStageResponse response = new DealFunnelStageResponse();
                    response.setStageId(rs.getLong("id"));
                    response.setStageName(rs.getString("stage_name"));
                    response.setWinProbability(rs.getInt("win_probability"));
                    response.setSortOrder(rs.getInt("sort_order"));
                    response.setDealCount(rs.getLong("deal_count"));
                    response.setTotalAmountCent(rs.getLong("total_amount"));
                    return response;
                });
    }

    private Integer count(JdbcTemplate jdbcTemplate, String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private Long sum(JdbcTemplate jdbcTemplate, String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }
}
