package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.request.SkillInstallReq;
import vip.mate.client.model.response.InstallResp;
import vip.mate.client.model.response.SkillUninstallResp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能安装客户端
 * <p>
 * 对应服务端 /api/v1/skills/install 接口，提供技能市场搜索、安装、卸载等功能
 */
public class SkillInstallClient extends AbstractApiClient {

    public SkillInstallClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 搜索 ClawHub 市场
     *
     * @param query 搜索关键词
     * @param limit 限制数量（默认 20）
     * @return 搜索结果列表
     */
    public R<List<InstallResp>> searchHub(String query, Integer limit) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", query);
        if (limit != null) {
            params.put("limit", limit);
        }
        return get(ApiPathConstants.SKILL_INSTALL_HUB_SEARCH, params,
                new ParameterizedTypeReference<R<List<InstallResp>>>() {});
    }

    /**
     * 开始异步安装技能
     *
     * @param request 安装请求参数
     * @return 安装任务信息
     */
    public R<InstallResp> startInstall(SkillInstallReq request) {
        return post(ApiPathConstants.SKILL_INSTALL_START, request,
                new ParameterizedTypeReference<R<InstallResp>>() {});
    }

    /**
     * 查询安装任务状态
     *
     * @param taskId 任务 ID
     * @return 任务状态
     */
    public R<InstallResp> getStatus(String taskId) {
        return get(resolvePath(ApiPathConstants.SKILL_INSTALL_STATUS, taskId),
                new ParameterizedTypeReference<R<InstallResp>>() {});
    }

    /**
     * 取消安装任务
     *
     * @param taskId 任务 ID
     * @return 操作结果
     */
    public R<Void> cancel(String taskId) {
        return post(resolvePath(ApiPathConstants.SKILL_INSTALL_CANCEL, taskId), null,
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 卸载技能
     *
     * @param skillName 技能名称
     * @return 卸载结果
     */
    public R<SkillUninstallResp> uninstall(String skillName) {
        return delete(resolvePath(ApiPathConstants.SKILL_INSTALL_BY_NAME, skillName),
                new ParameterizedTypeReference<R<SkillUninstallResp>>() {});
    }
}
