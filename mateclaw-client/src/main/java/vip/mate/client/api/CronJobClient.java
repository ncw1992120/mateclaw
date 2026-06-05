package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.request.CronJobReq;
import vip.mate.client.model.response.ActiveCronRunResp;
import vip.mate.client.model.response.CronJobResp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 定时任务管理客户端
 * <p>
 * 对应服务端 /api/v1/cron-jobs 接口，提供定时任务的增删改查、启停、手动执行等功能
 */
public class CronJobClient extends AbstractApiClient {

    public CronJobClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取定时任务列表
     *
     * @return 定时任务列表
     */
    public R<List<CronJobResp>> list() {
        return get(ApiPathConstants.CRON_JOB, new ParameterizedTypeReference<R<List<CronJobResp>>>() {});
    }

    /**
     * 获取定时任务详情
     *
     * @param id 定时任务 ID
     * @return 定时任务详情
     */
    public R<CronJobResp> get(Long id) {
        return get(resolvePath(ApiPathConstants.CRON_JOB_BY_ID, id), new ParameterizedTypeReference<R<CronJobResp>>() {});
    }

    /**
     * 创建定时任务
     *
     * @param dto 定时任务信息
     * @return 创建的定时任务信息
     */
    public R<CronJobResp> create(CronJobReq dto) {
        return post(ApiPathConstants.CRON_JOB, dto, new ParameterizedTypeReference<R<CronJobResp>>() {});
    }

    /**
     * 更新定时任务
     *
     * @param id  定时任务 ID
     * @param dto 定时任务更新信息
     * @return 更新后的定时任务信息
     */
    public R<CronJobResp> update(Long id, CronJobReq dto) {
        return put(resolvePath(ApiPathConstants.CRON_JOB_BY_ID, id), dto, new ParameterizedTypeReference<R<CronJobResp>>() {});
    }

    /**
     * 删除定时任务
     *
     * @param id 定时任务 ID
     * @return 操作结果
     */
    public R<Void> delete(Long id) {
        return delete(resolvePath(ApiPathConstants.CRON_JOB_BY_ID, id), new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 切换定时任务启用/禁用状态
     *
     * @param id      定时任务 ID
     * @param enabled 是否启用
     * @return 操作结果
     */
    public R<Void> toggle(Long id, boolean enabled) {
        String path = resolvePath(ApiPathConstants.CRON_JOB_TOGGLE, id) + "?enabled=" + enabled;
        return put(path, new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 手动立即执行定时任务
     *
     * @param id 定时任务 ID
     * @return 操作结果
     */
    public R<Void> runNow(Long id) {
        return post(resolvePath(ApiPathConstants.CRON_JOB_RUN, id), null, new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 获取活跃的定时任务运行记录
     *
     * @param conversationId 会话 ID
     * @return 活跃运行记录列表
     */
    public R<List<ActiveCronRunResp>> activeRuns(String conversationId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("conversationId", conversationId);
        return get(ApiPathConstants.CRON_JOB_ACTIVE_RUNS, params, new ParameterizedTypeReference<R<List<ActiveCronRunResp>>>() {});
    }
}
