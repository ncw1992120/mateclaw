package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.request.PatCreateReq;
import vip.mate.client.model.response.PatCreateResp;
import vip.mate.client.model.response.PersonalAccessTokenResp;

import java.util.List;

/**
 * 个人访问令牌客户端
 * <p>
 * 对应服务端 /api/v1/auth/tokens 接口，提供个人访问令牌管理功能
 */
public class PersonalAccessTokenClient extends AbstractApiClient {

    public PersonalAccessTokenClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取令牌列表
     *
     * @return 令牌列表
     */
    public R<List<PersonalAccessTokenResp>> list() {
        return get(ApiPathConstants.AUTH_TOKEN,
                new ParameterizedTypeReference<R<List<PersonalAccessTokenResp>>>() {});
    }

    /**
     * 创建令牌
     *
     * @param request 创建请求
     * @return 创建结果（含令牌值）
     */
    public R<PatCreateResp> create(PatCreateReq request) {
        return post(ApiPathConstants.AUTH_TOKEN, request,
                new ParameterizedTypeReference<R<PatCreateResp>>() {});
    }

    /**
     * 撤销令牌
     *
     * @param id 令牌 ID
     * @return 操作结果
     */
    public R<Void> revoke(Long id) {
        return delete(resolvePath(ApiPathConstants.AUTH_TOKEN_BY_ID, id),
                new ParameterizedTypeReference<R<Void>>() {});
    }
}
