package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.Workflow;
import vip.mate.client.model.request.DraftGenerateReq;
import vip.mate.client.model.request.WorkflowDraftReq;
import vip.mate.client.model.request.WorkflowPublishReq;
import vip.mate.client.model.response.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流客户端
 * <p>
 * 对应服务端 /api/v1/workflows 接口，提供工作流的创建、编辑、编译、发布、运行等管理功能
 */
public class WorkflowClient extends AbstractApiClient {

    public WorkflowClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取工作流列表
     *
     * @return 工作流列表
     */
    public R<List<Workflow>> list() {
        return get(ApiPathConstants.WORKFLOW, new ParameterizedTypeReference<R<List<Workflow>>>() {});
    }

    /**
     * 获取工作流详情
     *
     * @param id 工作流 ID
     * @return 工作流详情
     */
    public R<Workflow> get(Long id) {
        return get(resolvePath(ApiPathConstants.WORKFLOW_BY_ID, id),
                new ParameterizedTypeReference<R<Workflow>>() {});
    }

    /**
     * 创建工作流
     *
     * @param workflow 工作流信息
     * @return 创建的工作流信息
     */
    public R<Workflow> create(Workflow workflow) {
        return post(ApiPathConstants.WORKFLOW, workflow,
                new ParameterizedTypeReference<R<Workflow>>() {});
    }

    /**
     * 更新工作流
     *
     * @param id       工作流 ID
     * @param workflow 工作流更新信息
     * @return 更新后的工作流信息
     */
    public R<Workflow> update(Long id, Workflow workflow) {
        return put(resolvePath(ApiPathConstants.WORKFLOW_BY_ID, id), workflow,
                new ParameterizedTypeReference<R<Workflow>>() {});
    }

    /**
     * 保存工作流草稿
     *
     * @param id     工作流 ID
     * @param draft  草稿内容
     * @param userId 用户 ID
     * @return 保存后的工作流信息
     */
    public R<Workflow> saveDraft(Long id, WorkflowDraftReq draft, Long userId) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (userId != null) {
            params.put("userId", userId);
        }
        return put(resolvePath(ApiPathConstants.WORKFLOW_DRAFT, id), params, draft,
                new ParameterizedTypeReference<R<Workflow>>() {});
    }

    /**
     * 编译工作流草稿
     *
     * @param id 工作流 ID
     * @return 编译结果
     */
    public R<CompileResp> compileDraft(Long id) {
        return post(resolvePath(ApiPathConstants.WORKFLOW_COMPILE, id), null,
                new ParameterizedTypeReference<R<CompileResp>>() {});
    }

    /**
     * 发布工作流
     *
     * @param id       工作流 ID
     * @param request  发布请求
     * @param userId   用户 ID
     * @return 发布结果
     */
    public R<PublishOutcomeResp> publish(Long id, WorkflowPublishReq request, Long userId) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (userId != null) {
            params.put("userId", userId);
        }
        return post(resolvePath(ApiPathConstants.WORKFLOW_PUBLISH, id), params, request,
                new ParameterizedTypeReference<R<PublishOutcomeResp>>() {});
    }

    /**
     * 删除工作流
     *
     * @param id 工作流 ID
     * @return 操作结果
     */
    public R<Void> delete(Long id) {
        return delete(resolvePath(ApiPathConstants.WORKFLOW_BY_ID, id),
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 获取工作流运行记录
     *
     * @param id    工作流 ID
     * @param limit 限制数量（默认 50）
     * @return 运行记录列表
     */
    public R<List<WorkflowRunResp>> listRuns(Long id, Integer limit) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (limit != null) {
            params.put("limit", limit);
        }
        return get(resolvePath(ApiPathConstants.WORKFLOW_RUNS, id), params,
                new ParameterizedTypeReference<R<List<WorkflowRunResp>>>() {});
    }

    /**
     * 获取暂停的运行
     *
     * @param limit 限制数量（默认 50）
     * @return 暂停的运行列表
     */
    public R<List<PausedRunSummaryResp>> listPausedRuns(Integer limit) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (limit != null) {
            params.put("limit", limit);
        }
        return get(ApiPathConstants.WORKFLOW_RUNS_PAUSED, params,
                new ParameterizedTypeReference<R<List<PausedRunSummaryResp>>>() {});
    }

    /**
     * 获取运行详情
     *
     * @param runId 运行 ID
     * @return 运行详情
     */
    public R<RunDetailResp> getRun(Long runId) {
        return get(resolvePath(ApiPathConstants.WORKFLOW_RUN_BY_ID, runId),
                new ParameterizedTypeReference<R<RunDetailResp>>() {});
    }

    /**
     * 生成工作流草稿
     *
     * @param request 生成请求
     * @return 生成结果
     */
    public R<GeneratedWorkflowDraftResp> generateDraft(DraftGenerateReq request) {
        return post(ApiPathConstants.WORKFLOW_DRAFT_GENERATE, request,
                new ParameterizedTypeReference<R<GeneratedWorkflowDraftResp>>() {});
    }

    /**
     * 获取草稿模板列表
     *
     * @return 模板列表
     */
    public R<List<WorkflowDraftTemplateResp>> listDraftTemplates() {
        return get(ApiPathConstants.WORKFLOW_DRAFT_TEMPLATES,
                new ParameterizedTypeReference<R<List<WorkflowDraftTemplateResp>>>() {});
    }

    /**
     * 预览编译草稿
     *
     * @param draft 草稿内容
     * @return 编译结果
     */
    public R<Void> previewCompile(WorkflowDraftReq draft) {
        return post(ApiPathConstants.WORKFLOW_DRAFT_PREVIEW_COMPILE, draft,
                new ParameterizedTypeReference<R<Void>>() {});
    }
}
