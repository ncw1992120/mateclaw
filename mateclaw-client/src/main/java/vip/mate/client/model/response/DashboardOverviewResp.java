package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * Dashboard 概览统计数据
 */
@Data
public class DashboardOverviewResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 今日统计 */
    private DashboardStatsResp today;

    /** 本周统计 */
    private DashboardStatsResp thisWeek;

    /** 本月统计 */
    private DashboardStatsResp thisMonth;
}
