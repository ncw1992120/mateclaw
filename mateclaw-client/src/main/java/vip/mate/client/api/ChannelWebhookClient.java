package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 渠道 Webhook 客户端
 * <p>
 * 对应服务端 /api/v1/channels/webhook 接口，提供各渠道 Webhook 回调和注册功能。
 * 服务端直接返回 ResponseEntity，不走 R&lt;T&gt; 包装，返回数据结构取决于具体渠道实现。
 */
public class ChannelWebhookClient extends AbstractApiClient {

    public ChannelWebhookClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 钉钉 Webhook 回调
     *
     * @param body 回调数据
     * @return 响应结果
     */
    public Map<String, Object> dingtalkWebhook(Map<String, Object> body) {
        return post(ApiPathConstants.CHANNEL_WEBHOOK_DINGTALK, body,
                new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    /**
     * 开始钉钉注册
     *
     * @return 注册信息
     */
    public Map<String, Object> dingtalkRegisterBegin() {
        return post(ApiPathConstants.CHANNEL_WEBHOOK_DINGTALK_REGISTER_BEGIN, null,
                new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    /**
     * 查询钉钉注册状态
     *
     * @param session 会话 ID
     * @return 注册状态
     */
    public Map<String, Object> dingtalkRegisterStatus(String session) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("session", session);
        return get(ApiPathConstants.CHANNEL_WEBHOOK_DINGTALK_REGISTER_STATUS, params,
                new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    /**
     * 飞书 Webhook 回调
     *
     * @param body 回调数据
     * @return 响应结果
     */
    public Map<String, Object> feishuWebhook(Map<String, Object> body) {
        return post(ApiPathConstants.CHANNEL_WEBHOOK_FEISHU, body,
                new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    /**
     * 开始飞书注册
     *
     * @param domain 域名（默认 feishu）
     * @return 注册信息
     */
    public Map<String, Object> feishuRegisterBegin(String domain) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (domain != null) {
            params.put("domain", domain);
        }
        return post(ApiPathConstants.CHANNEL_WEBHOOK_FEISHU_REGISTER_BEGIN, params, null,
                new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    /**
     * 查询飞书注册状态
     *
     * @param session 会话 ID
     * @return 注册状态
     */
    public Map<String, Object> feishuRegisterStatus(String session) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("session", session);
        return get(ApiPathConstants.CHANNEL_WEBHOOK_FEISHU_REGISTER_STATUS, params,
                new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    /**
     * Telegram Webhook 回调
     *
     * @param body 回调数据
     * @return 响应结果
     */
    public String telegramWebhook(Map<String, Object> body) {
        return post(ApiPathConstants.CHANNEL_WEBHOOK_TELEGRAM, body,
                new ParameterizedTypeReference<String>() {});
    }

    /**
     * Discord Webhook 回调
     *
     * @param body 回调数据
     * @return 响应结果
     */
    public Map<String, Object> discordWebhook(Map<String, Object> body) {
        return post(ApiPathConstants.CHANNEL_WEBHOOK_DISCORD, body,
                new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    /**
     * 企业微信 Webhook 回调
     *
     * @param body 回调数据
     * @return 响应结果
     */
    public String wecomWebhook(Map<String, Object> body) {
        return post(ApiPathConstants.CHANNEL_WEBHOOK_WECOM, body,
                new ParameterizedTypeReference<String>() {});
    }

    /**
     * Slack Webhook 回调
     *
     * @param body 回调数据
     * @return 响应结果
     */
    public Map<String, Object> slackWebhook(Map<String, Object> body) {
        return post(ApiPathConstants.CHANNEL_WEBHOOK_SLACK, body,
                new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    /**
     * 获取微信二维码
     *
     * @return 二维码信息
     */
    public Map<String, Object> weixinQrcode() {
        return get(ApiPathConstants.CHANNEL_WEBHOOK_WEIXIN_QRCODE,
                new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    /**
     * 查询微信二维码状态
     *
     * @param qrcode 二维码 ID
     * @return 状态信息
     */
    public Map<String, Object> weixinQrcodeStatus(String qrcode) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("qrcode", qrcode);
        return get(ApiPathConstants.CHANNEL_WEBHOOK_WEIXIN_QRCODE_STATUS, params,
                new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    /**
     * 获取 Webhook 状态
     *
     * @return 状态信息
     */
    public Map<String, Object> status() {
        return get(ApiPathConstants.CHANNEL_WEBHOOK_STATUS,
                new ParameterizedTypeReference<Map<String, Object>>() {});
    }
}