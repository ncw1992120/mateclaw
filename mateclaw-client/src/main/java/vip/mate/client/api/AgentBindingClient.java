package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.response.AgentProviderPreferenceResp;
import vip.mate.client.model.response.AgentSkillBindingResp;
import vip.mate.client.model.response.AgentToolBindingResp;

import java.util.List;

/**
 * Agent 绑定客户端
 * <p>
 * 对应服务端 /api/v1/agents/{agentId} 绑定接口，提供 Skill、Tool、Provider 偏好绑定管理
 */
public class AgentBindingClient extends AbstractApiClient {

    public AgentBindingClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取 Agent 已绑定的 Skill 列表
     *
     * @param agentId Agent ID
     * @return Skill 绑定列表
     */
    public R<List<AgentSkillBindingResp>> listSkills(Long agentId) {
        return get(resolvePath(ApiPathConstants.AGENT_SKILLS, agentId),
                new ParameterizedTypeReference<R<List<AgentSkillBindingResp>>>() {});
    }

    /**
     * 批量设置 Agent Skill 绑定
     *
     * @param agentId  Agent ID
     * @param skillIds Skill ID 列表
     * @return 操作结果
     */
    public R<Void> setSkills(Long agentId, List<Long> skillIds) {
        return put(resolvePath(ApiPathConstants.AGENT_SKILLS, agentId), skillIds,
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 绑定单个 Skill
     *
     * @param agentId Agent ID
     * @param skillId Skill ID
     * @return 绑定信息
     */
    public R<AgentSkillBindingResp> bindSkill(Long agentId, Long skillId) {
        return post(resolvePath(ApiPathConstants.AGENT_SKILL_BY_ID, agentId, skillId), null,
                new ParameterizedTypeReference<R<AgentSkillBindingResp>>() {});
    }

    /**
     * 解绑单个 Skill
     *
     * @param agentId Agent ID
     * @param skillId Skill ID
     * @return 操作结果
     */
    public R<Void> unbindSkill(Long agentId, Long skillId) {
        return delete(resolvePath(ApiPathConstants.AGENT_SKILL_BY_ID, agentId, skillId),
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 获取 Agent 已绑定的 Tool 列表
     *
     * @param agentId Agent ID
     * @return Tool 绑定列表
     */
    public R<List<AgentToolBindingResp>> listTools(Long agentId) {
        return get(resolvePath(ApiPathConstants.AGENT_TOOLS, agentId),
                new ParameterizedTypeReference<R<List<AgentToolBindingResp>>>() {});
    }

    /**
     * 批量设置 Agent Tool 绑定
     *
     * @param agentId   Agent ID
     * @param toolNames Tool 名称列表
     * @return 操作结果
     */
    public R<Void> setTools(Long agentId, List<String> toolNames) {
        return put(resolvePath(ApiPathConstants.AGENT_TOOLS, agentId), toolNames,
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 获取 Agent Provider 偏好顺序
     *
     * @param agentId Agent ID
     * @return Provider 偏好列表
     */
    public R<List<AgentProviderPreferenceResp>> listProviderPreferences(Long agentId) {
        return get(resolvePath(ApiPathConstants.AGENT_PROVIDER_PREFERENCES, agentId),
                new ParameterizedTypeReference<R<List<AgentProviderPreferenceResp>>>() {});
    }

    /**
     * 设置 Agent Provider 偏好顺序
     *
     * @param agentId     Agent ID
     * @param providerIds Provider ID 列表（按优先级排序）
     * @return 操作结果
     */
    public R<Void> setProviderPreferences(Long agentId, List<String> providerIds) {
        return put(resolvePath(ApiPathConstants.AGENT_PROVIDER_PREFERENCES, agentId), providerIds,
                new ParameterizedTypeReference<R<Void>>() {});
    }
}
