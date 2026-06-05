package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 渠道二维码客户端
 * <p>
 * 对应服务端 /api/v1/channels/qrcode 接口，提供渠道二维码注册功能。
 * 服务端直接返回 ResponseEntity，不走 R&lt;T&gt; 包装，返回数据结构取决于具体渠道实现。
 */
public class ChannelQRCodeClient extends AbstractApiClient {

    public ChannelQRCodeClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 开始二维码注册
     *
     * @param channelType 渠道类型
     * @param params      可选参数（取决于各渠道实现）
     * @return 注册信息
     */
    public Map<String, Object> begin(String channelType, Map<String, String> params) {
        Map<String, Object> queryParams = params != null ? new LinkedHashMap<>(params) : new LinkedHashMap<>();
        return post(buildUrl(resolvePath(ApiPathConstants.CHANNEL_QRCODE_BEGIN, channelType), queryParams), null,
                new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    /**
     * 查询二维码注册状态
     *
     * @param channelType 渠道类型
     * @param session     会话 ID
     * @return 注册状态
     */
    public Map<String, Object> status(String channelType, String session) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("session", session);
        return get(resolvePath(ApiPathConstants.CHANNEL_QRCODE_STATUS, channelType), params,
                new ParameterizedTypeReference<Map<String, Object>>() {});
    }
}