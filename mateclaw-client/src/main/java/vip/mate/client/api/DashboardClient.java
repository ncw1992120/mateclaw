package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.response.CronJobRunResp;
import vip.mate.client.model.response.DashboardOverviewResp;
import vip.mate.client.model.response.UsageDailyResp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard 统计客户端
 * <p>
 * 对应服务端 /api/v1/dashboard 接口，提供概览统计、趋势分析、运行记录查询等功能
 */
public class DashboardClient extends AbstractApiClient {

    public DashboardClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取 Dashboard 概览数据
     *
     * @return 概览统计数据
     */
    public R<DashboardOverviewResp> overview() {
        return get(ApiPathConstants.DASHBOARD_OVERVIEW, new ParameterizedTypeReference<R<DashboardOverviewResp>>() {});
    }

    /**
     * 获取趋势数据
     *
     * @param days 统计天数
     * @return 趋势数据列表
     */
    public R<List<UsageDailyResp>> trend(int days) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("days", days);
        return get(ApiPathConstants.DASHBOARD_TREND, params, new ParameterizedTypeReference<R<List<UsageDailyResp>>>() {});
    }

    /**
     * 获取指定定时任务的运行记录
     *
     * @param cronJobId 定时任务 ID
     * @param limit     记录数量限制
     * @return 运行记录列表
     */
    public R<List<CronJobRunResp>> cronJobRuns(Long cronJobId, int limit) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("limit", limit);
        return get(resolvePath(ApiPathConstants.DASHBOARD_CRON_RUNS_BY_ID, cronJobId), params, new ParameterizedTypeReference<R<List<CronJobRunResp>>>() {});
    }

    /**
     * 获取最近的运行记录
     *
     * @param limit 记录数量限制
     * @return 运行记录列表
     */
    public R<List<CronJobRunResp>> recentRuns(int limit) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("limit", limit);
        return get(ApiPathConstants.DASHBOARD_CRON_RUNS, params, new ParameterizedTypeReference<R<List<CronJobRunResp>>>() {});
    }
}
