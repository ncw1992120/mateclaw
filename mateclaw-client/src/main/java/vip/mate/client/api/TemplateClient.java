package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.response.TemplateResp;

import java.util.List;

/**
 * 模板客户端
 * <p>
 * 对应服务端 /api/v1/templates 接口，提供模板列表和应用功能
 */
public class TemplateClient extends AbstractApiClient {

    public TemplateClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取模板列表
     *
     * @return 模板列表
     */
    public R<List<TemplateResp>> list() {
        return get(ApiPathConstants.TEMPLATE, new ParameterizedTypeReference<R<List<TemplateResp>>>() {});
    }

    /**
     * 应用模板创建 Agent
     *
     * @param id 模板 ID
     * @return 创建的 Agent 信息
     */
    public R<TemplateResp> apply(String id) {
        return post(resolvePath(ApiPathConstants.TEMPLATE_APPLY, id), null,
                new ParameterizedTypeReference<R<TemplateResp>>() {});
    }
}
