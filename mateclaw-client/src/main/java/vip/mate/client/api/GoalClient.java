package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.request.GoalCreateReq;
import vip.mate.client.model.request.GoalCriterionReq;
import vip.mate.client.model.request.GoalUpdateReq;
import vip.mate.client.model.response.GoalEvaluationResp;
import vip.mate.client.model.response.GoalEventResp;
import vip.mate.client.model.response.GoalResp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 目标管理客户端
 * <p>
 * 对应服务端 /api/v1/goals 接口，提供目标的创建、查询、更新、暂停/恢复/放弃等生命周期管理功能
 */
public class GoalClient extends AbstractApiClient {

    public GoalClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 创建目标
     *
     * @param req 目标创建请求参数
     * @return 创建的目标信息
     */
    public R<GoalResp> create(GoalCreateReq req) {
        return post(ApiPathConstants.GOAL, req, new ParameterizedTypeReference<R<GoalResp>>() {});
    }

    /**
     * 根据会话 ID 查找活跃目标
     *
     * @param conversationId 会话 ID
     * @return 活跃目标信息
     */
    public R<GoalResp> findActive(String conversationId) {
        return get(resolvePath(ApiPathConstants.GOAL_BY_CONVERSATION, conversationId), new ParameterizedTypeReference<R<GoalResp>>() {});
    }

    /**
     * 获取目标详情
     *
     * @param id 目标 ID
     * @return 目标详情
     */
    public R<GoalResp> get(Long id) {
        return get(resolvePath(ApiPathConstants.GOAL_BY_ID, id), new ParameterizedTypeReference<R<GoalResp>>() {});
    }

    /**
     * 获取目标事件列表
     *
     * @param id    目标 ID
     * @param limit 事件数量限制
     * @return 目标事件列表
     */
    public R<List<GoalEventResp>> events(Long id, int limit) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("limit", limit);
        return get(resolvePath(ApiPathConstants.GOAL_EVENTS, id), params, new ParameterizedTypeReference<R<List<GoalEventResp>>>() {});
    }

    /**
     * 获取目标列表
     *
     * @param status 目标状态（可选）
     * @param limit  数量限制
     * @return 目标列表
     */
    public R<List<GoalResp>> list(String status, int limit) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("limit", limit);
        if (status != null && !status.isBlank()) {
            params.put("status", status);
        }
        return get(ApiPathConstants.GOAL, params, new ParameterizedTypeReference<R<List<GoalResp>>>() {});
    }

    /**
     * 更新目标
     *
     * @param id  目标 ID
     * @param req 目标更新请求参数
     * @return 更新后的目标信息
     */
    public R<GoalResp> update(Long id, GoalUpdateReq req) {
        return patch(resolvePath(ApiPathConstants.GOAL_BY_ID, id), req, new ParameterizedTypeReference<R<GoalResp>>() {});
    }

    /**
     * 暂停目标
     *
     * @param id 目标 ID
     * @return 更新后的目标信息
     */
    public R<GoalResp> pause(Long id) {
        return post(resolvePath(ApiPathConstants.GOAL_PAUSE, id), null, new ParameterizedTypeReference<R<GoalResp>>() {});
    }

    /**
     * 恢复目标
     *
     * @param id 目标 ID
     * @return 更新后的目标信息
     */
    public R<GoalResp> resume(Long id) {
        return post(resolvePath(ApiPathConstants.GOAL_RESUME, id), null, new ParameterizedTypeReference<R<GoalResp>>() {});
    }

    /**
     * 放弃目标
     *
     * @param id 目标 ID
     * @return 更新后的目标信息
     */
    public R<GoalResp> abandon(Long id) {
        return post(resolvePath(ApiPathConstants.GOAL_ABANDON, id), null, new ParameterizedTypeReference<R<GoalResp>>() {});
    }

    /**
     * 为目标添加评判标准
     *
     * @param id        目标 ID
     * @param request   评判标准请求
     * @return 更新后的目标信息
     */
    public R<GoalResp> addCriterion(Long id, GoalCriterionReq request) {
        return post(resolvePath(ApiPathConstants.GOAL_CRITERIA, id), request, new ParameterizedTypeReference<R<GoalResp>>() {});
    }

    /**
     * 评估目标
     *
     * @param id 目标 ID
     * @return 目标评估结果
     */
    public R<GoalEvaluationResp> evaluate(Long id) {
        return post(resolvePath(ApiPathConstants.GOAL_BY_ID, id) + "/evaluate", null, new ParameterizedTypeReference<R<GoalEvaluationResp>>() {});
    }
}
