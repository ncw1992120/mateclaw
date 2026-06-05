package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.WikiTransformation;
import vip.mate.client.model.request.WikiTransformationApplyReq;
import vip.mate.client.model.response.SaveRunAsPageResp;
import vip.mate.client.model.response.TransformationAggregateResp;
import vip.mate.client.model.response.WikiTransformationRunResp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wiki 转换客户端
 * <p>
 * 对应服务端 /api/v1/wiki/transformations 接口，提供知识库转换管理功能
 */
public class WikiTransformationClient extends AbstractApiClient {

    public WikiTransformationClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取转换列表
     *
     * @param kbId 知识库 ID（可选）
     * @return 转换列表
     */
    public R<List<WikiTransformation>> list(Long kbId) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (kbId != null) {
            params.put("kbId", kbId);
        }
        return get(ApiPathConstants.WIKI_TRANSFORMATION, params,
                new ParameterizedTypeReference<R<List<WikiTransformation>>>() {});
    }

    /**
     * 获取转换详情
     *
     * @param id 转换 ID
     * @return 转换详情
     */
    public R<WikiTransformation> get(Long id) {
        return get(resolvePath(ApiPathConstants.WIKI_TRANSFORMATION_BY_ID, id),
                new ParameterizedTypeReference<R<WikiTransformation>>() {});
    }

    /**
     * 创建转换
     *
     * @param transformation 转换信息
     * @return 创建的转换信息
     */
    public R<WikiTransformation> create(WikiTransformation transformation) {
        return post(ApiPathConstants.WIKI_TRANSFORMATION, transformation,
                new ParameterizedTypeReference<R<WikiTransformation>>() {});
    }

    /**
     * 更新转换
     *
     * @param id            转换 ID
     * @param transformation 转换更新信息
     * @return 更新后的转换信息
     */
    public R<WikiTransformation> update(Long id, WikiTransformation transformation) {
        return put(resolvePath(ApiPathConstants.WIKI_TRANSFORMATION_BY_ID, id), transformation,
                new ParameterizedTypeReference<R<WikiTransformation>>() {});
    }

    /**
     * 删除转换
     *
     * @param id 转换 ID
     * @return 操作结果
     */
    public R<Void> delete(Long id) {
        return delete(resolvePath(ApiPathConstants.WIKI_TRANSFORMATION_BY_ID, id),
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 应用转换
     *
     * @param id      转换 ID
     * @param request 应用请求参数
     * @param sync    是否同步执行
     * @return 转换运行信息
     */
    public R<WikiTransformationRunResp> apply(Long id, WikiTransformationApplyReq request, Boolean sync) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (sync != null) {
            params.put("sync", sync);
        }
        return post(buildUrl(resolvePath(ApiPathConstants.WIKI_TRANSFORMATION_APPLY, id), params), request,
                new ParameterizedTypeReference<R<WikiTransformationRunResp>>() {});
    }

    /**
     * 聚合转换结果
     *
     * @param id   转换 ID
     * @param kbId 知识库 ID
     * @return 聚合结果
     */
    public R<TransformationAggregateResp> aggregate(Long id, Long kbId) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (kbId != null) {
            params.put("kbId", kbId);
        }
        return post(buildUrl(resolvePath(ApiPathConstants.WIKI_TRANSFORMATION_AGGREGATE, id), params), null,
                new ParameterizedTypeReference<R<TransformationAggregateResp>>() {});
    }

    /**
     * 获取运行详情
     *
     * @param runId 运行 ID
     * @return 运行详情
     */
    public R<WikiTransformationRunResp> getRun(Long runId) {
        return get(resolvePath(ApiPathConstants.WIKI_TRANSFORMATION_RUN_BY_ID, runId),
                new ParameterizedTypeReference<R<WikiTransformationRunResp>>() {});
    }

    /**
     * 获取运行列表
     *
     * @param rawId            原始材料 ID（可选）
     * @param kbId             知识库 ID（可选）
     * @param transformationId 转换 ID（可选）
     * @param limit            限制数量（可选）
     * @return 运行列表
     */
    public R<List<WikiTransformationRunResp>> listRuns(Long rawId, Long kbId, Long transformationId, Integer limit) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (rawId != null) {
            params.put("rawId", rawId);
        }
        if (kbId != null) {
            params.put("kbId", kbId);
        }
        if (transformationId != null) {
            params.put("transformationId", transformationId);
        }
        if (limit != null) {
            params.put("limit", limit);
        }
        return get(ApiPathConstants.WIKI_TRANSFORMATION_RUNS, params,
                new ParameterizedTypeReference<R<List<WikiTransformationRunResp>>>() {});
    }

    /**
     * 取消运行
     *
     * @param runId 运行 ID
     * @return 操作结果
     */
    public R<Void> cancelRun(Long runId) {
        return post(resolvePath(ApiPathConstants.WIKI_TRANSFORMATION_RUN_CANCEL, runId), null,
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 将运行结果保存为页面
     *
     * @param runId 运行 ID
     * @return 保存结果
     */
    public R<SaveRunAsPageResp> saveRunAsPage(Long runId) {
        return post(resolvePath(ApiPathConstants.WIKI_TRANSFORMATION_RUN_SAVE_AS_PAGE, runId), null,
                new ParameterizedTypeReference<R<SaveRunAsPageResp>>() {});
    }

    /**
     * 删除运行
     *
     * @param runId 运行 ID
     * @return 操作结果
     */
    public R<Void> deleteRun(Long runId) {
        return delete(resolvePath(ApiPathConstants.WIKI_TRANSFORMATION_RUN_DELETE, runId),
                new ParameterizedTypeReference<R<Void>>() {});
    }
}
