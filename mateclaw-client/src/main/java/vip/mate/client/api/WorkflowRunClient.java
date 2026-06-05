package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.request.WorkflowResumeReq;
import vip.mate.client.model.response.WorkflowResumeResp;

/**
 * 工作流运行客户端
 * <p>
 * 对应服务端 /api/v1/workflows/runs 接口，提供工作流运行的恢复功能
 */
public class WorkflowRunClient extends AbstractApiClient {

    public WorkflowRunClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 恢复工作流运行
     *
     * @param runId   运行 ID
     * @param request 恢复请求参数
     * @return 恢复结果
     */
    public R<WorkflowResumeResp> resume(Long runId, WorkflowResumeReq request) {
        return post(resolvePath(ApiPathConstants.WORKFLOW_RUN_RESUME, runId), request,
                new ParameterizedTypeReference<R<WorkflowResumeResp>>() {});
    }
}
