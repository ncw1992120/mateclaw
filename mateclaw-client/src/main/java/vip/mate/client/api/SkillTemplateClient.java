package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.Skill;
import vip.mate.client.model.request.SkillTemplateInstantiateReq;
import vip.mate.client.model.response.TemplateResp;

import java.util.List;

/**
 * 技能模板客户端
 * <p>
 * 对应服务端 /api/v1/skill-templates 接口，提供技能模板管理功能
 */
public class SkillTemplateClient extends AbstractApiClient {

    public SkillTemplateClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取技能模板列表
     *
     * @return 模板列表
     */
    public R<List<TemplateResp>> list() {
        return get(ApiPathConstants.SKILL_TEMPLATE,
                new ParameterizedTypeReference<R<List<TemplateResp>>>() {});
    }

    /**
     * 获取技能模板详情
     *
     * @param id 模板 ID
     * @return 模板详情
     */
    public R<TemplateResp> get(Long id) {
        return get(resolvePath(ApiPathConstants.SKILL_TEMPLATE_BY_ID, id),
                new ParameterizedTypeReference<R<TemplateResp>>() {});
    }

    /**
     * 实例化技能模板
     *
     * @param id      模板 ID
     * @param request 模板变量键值对
     * @return 创建的技能
     */
    public R<Skill> instantiate(Long id, SkillTemplateInstantiateReq request) {
        return post(resolvePath(ApiPathConstants.SKILL_TEMPLATE_INSTANTIATE, id), request,
                new ParameterizedTypeReference<R<Skill>>() {});
    }
}
