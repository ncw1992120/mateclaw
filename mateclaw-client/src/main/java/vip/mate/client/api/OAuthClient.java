package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.request.DeviceCodePollReq;
import vip.mate.client.model.request.OAuthDeviceCancelReq;
import vip.mate.client.model.request.OAuthPasteReq;
import vip.mate.client.model.response.*;

/**
 * OAuth 客户端
 * <p>
 * 对应服务端 /api/v1/oauth 接口，提供 OpenAI 和 Anthropic OAuth 认证管理功能
 */
public class OAuthClient extends AbstractApiClient {

    public OAuthClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    // ==================== OpenAI OAuth ====================

    /**
     * 获取 OpenAI OAuth 授权 URL
     *
     * @return 授权结果
     */
    public R<OAuthAuthorizeResp> openaiAuthorize() {
        return get(ApiPathConstants.OAUTH_OPENAI_AUTHORIZE,
                new ParameterizedTypeReference<R<OAuthAuthorizeResp>>() {});
    }

    /**
     * OpenAI OAuth 回调粘贴模式
     *
     * @param request 回调请求
     * @return 操作结果
     */
    public R<Void> openaiCallbackPaste(OAuthPasteReq request) {
        return post(ApiPathConstants.OAUTH_OPENAI_CALLBACK_PASTE, request,
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * OpenAI Device Flow 开始
     *
     * @return Device Code 信息
     */
    public R<DeviceCodeStartResp> openaiDeviceStart() {
        return post(ApiPathConstants.OAUTH_OPENAI_DEVICE_START, null,
                new ParameterizedTypeReference<R<DeviceCodeStartResp>>() {});
    }

    /**
     * OpenAI Device Flow 轮询
     *
     * @param request 轮询请求
     * @return 轮询结果
     */
    public R<DeviceCodePollResp> openaiDevicePoll(DeviceCodePollReq request) {
        return post(ApiPathConstants.OAUTH_OPENAI_DEVICE_POLL, request,
                new ParameterizedTypeReference<R<DeviceCodePollResp>>() {});
    }

    /**
     * OpenAI Device Flow 取消
     *
     * @param request 取消请求
     * @return 操作结果
     */
    public R<Void> openaiDeviceCancel(OAuthDeviceCancelReq request) {
        return post(ApiPathConstants.OAUTH_OPENAI_DEVICE_CANCEL, request,
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 刷新 OpenAI OAuth Token
     *
     * @return 操作结果
     */
    public R<Void> openaiRefresh() {
        return post(ApiPathConstants.OAUTH_OPENAI_REFRESH, null,
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 撤销 OpenAI OAuth 授权
     *
     * @return 操作结果
     */
    public R<Void> openaiRevoke() {
        return delete(ApiPathConstants.OAUTH_OPENAI_REVOKE,
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 获取 OpenAI OAuth 状态
     *
     * @return OAuth 状态
     */
    public R<OAuthStatusResp> openaiStatus() {
        return get(ApiPathConstants.OAUTH_OPENAI_STATUS,
                new ParameterizedTypeReference<R<OAuthStatusResp>>() {});
    }

    // ==================== Anthropic OAuth ====================

    /**
     * 获取 Anthropic OAuth 状态
     *
     * @return OAuth 状态
     */
    public R<AnthropicOAuthStatusResp> anthropicStatus() {
        return get(ApiPathConstants.OAUTH_ANTHROPIC_STATUS,
                new ParameterizedTypeReference<R<AnthropicOAuthStatusResp>>() {});
    }

    /**
     * 重载 Anthropic OAuth 凭证
     *
     * @return OAuth 状态
     */
    public R<AnthropicOAuthStatusResp> anthropicReload() {
        return post(ApiPathConstants.OAUTH_ANTHROPIC_RELOAD, null,
                new ParameterizedTypeReference<R<AnthropicOAuthStatusResp>>() {});
    }
}
