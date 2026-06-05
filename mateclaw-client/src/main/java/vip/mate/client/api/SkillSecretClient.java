package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.request.SkillSecretPutReq;
import vip.mate.client.model.response.SecretSummaryResp;

import java.util.List;

/**
 * 技能密钥客户端
 * <p>
 * 对应服务端 /api/v1/skills/{skillId}/secrets 接口，提供技能密钥管理功能
 */
public class SkillSecretClient extends AbstractApiClient {

    public SkillSecretClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取密钥列表
     *
     * @param skillId 技能 ID
     * @return 密钥摘要列表
     */
    public R<List<SecretSummaryResp>> list(Long skillId) {
        return get(resolvePath(ApiPathConstants.SKILL_SECRET, skillId),
                new ParameterizedTypeReference<R<List<SecretSummaryResp>>>() {});
    }

    /**
     * 写入密钥
     *
     * @param skillId 技能 ID
     * @param request 密钥写入请求
     * @return 操作结果
     */
    public R<Void> put(Long skillId, SkillSecretPutReq request) {
        return post(resolvePath(ApiPathConstants.SKILL_SECRET, skillId), request,
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 删除密钥
     *
     * @param skillId 技能 ID
     * @param key     密钥 Key
     * @return 操作结果
     */
    public R<Void> remove(Long skillId, String key) {
        return delete(resolvePath(ApiPathConstants.SKILL_SECRET_BY_KEY, skillId, key),
                new ParameterizedTypeReference<R<Void>>() {});
    }
}
