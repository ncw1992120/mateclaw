package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.response.WorkspaceFileResp;

import java.util.List;

/**
 * 工作区文件管理客户端
 */
public class WorkspaceFileClient extends AbstractApiClient {

    public WorkspaceFileClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取工作区文件列表
     */
    public R<List<WorkspaceFileResp>> listFiles(Long agentId) {
        return get(resolvePath(ApiPathConstants.AGENT_WORKSPACE_FILES, agentId),
                new ParameterizedTypeReference<R<List<WorkspaceFileResp>>>() {});
    }

    /**
     * 获取工作区文件内容
     */
    public R<WorkspaceFileResp> getFile(Long agentId, String filename) {
        return get(resolvePath(ApiPathConstants.AGENT_WORKSPACE_FILE_BY_NAME, agentId, filename),
                new ParameterizedTypeReference<R<WorkspaceFileResp>>() {});
    }

    /**
     * 保存工作区文件
     */
    public R<WorkspaceFileResp> saveFile(Long agentId, String filename, String content) {
        return put(resolvePath(ApiPathConstants.AGENT_WORKSPACE_FILE_BY_NAME, agentId, filename), content,
                new ParameterizedTypeReference<R<WorkspaceFileResp>>() {});
    }

    /**
     * 删除工作区文件
     */
    public R<Void> deleteFile(Long agentId, String filename) {
        return delete(resolvePath(ApiPathConstants.AGENT_WORKSPACE_FILE_BY_NAME, agentId, filename),
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 获取提示词文件列表
     */
    public R<List<String>> getPromptFiles(Long agentId) {
        return get(resolvePath(ApiPathConstants.AGENT_PROMPT_FILES, agentId),
                new ParameterizedTypeReference<R<List<String>>>() {});
    }

    /**
     * 设置提示词文件列表
     */
    public R<Void> setPromptFiles(Long agentId, List<String> files) {
        return put(resolvePath(ApiPathConstants.AGENT_PROMPT_FILES, agentId), files,
                new ParameterizedTypeReference<R<Void>>() {});
    }
}
