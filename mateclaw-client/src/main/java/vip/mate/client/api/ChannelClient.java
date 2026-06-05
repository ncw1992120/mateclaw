package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.Channel;
import vip.mate.client.model.R;
import vip.mate.client.model.request.ChannelPreflightReq;
import vip.mate.client.model.response.ChannelHealthResp;
import vip.mate.client.model.response.ChannelPreflightResp;
import vip.mate.client.model.response.ChannelStatusResp;

import java.util.List;

/**
 * 渠道管理客户端
 * <p>
 * 对应服务端 /api/v1/channels 接口，提供渠道的增删改查、启停、健康检查等功能
 */
public class ChannelClient extends AbstractApiClient {

    public ChannelClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取渠道列表
     *
     * @return 渠道列表
     */
    public R<List<Channel>> list() {
        return get(ApiPathConstants.CHANNEL, new ParameterizedTypeReference<R<List<Channel>>>() {});
    }

    /**
     * 按类型获取渠道列表
     *
     * @param channelType 渠道类型
     * @return 渠道列表
     */
    public R<List<Channel>> listByType(String channelType) {
        return get(resolvePath(ApiPathConstants.CHANNEL_BY_TYPE, channelType), new ParameterizedTypeReference<R<List<Channel>>>() {});
    }

    /**
     * 获取渠道详情
     *
     * @param id 渠道 ID
     * @return 渠道详情
     */
    public R<Channel> get(Long id) {
        return get(resolvePath(ApiPathConstants.CHANNEL_BY_ID, id), new ParameterizedTypeReference<R<Channel>>() {});
    }

    /**
     * 创建渠道
     *
     * @param channel 渠道信息
     * @return 创建的渠道信息
     */
    public R<Channel> create(Channel channel) {
        return post(ApiPathConstants.CHANNEL, channel, new ParameterizedTypeReference<R<Channel>>() {});
    }

    /**
     * 更新渠道
     *
     * @param id      渠道 ID
     * @param channel 渠道更新信息
     * @return 更新后的渠道信息
     */
    public R<Channel> update(Long id, Channel channel) {
        return put(resolvePath(ApiPathConstants.CHANNEL_BY_ID, id), channel, new ParameterizedTypeReference<R<Channel>>() {});
    }

    /**
     * 删除渠道
     *
     * @param id 渠道 ID
     * @return 操作结果
     */
    public R<Void> delete(Long id) {
        return delete(resolvePath(ApiPathConstants.CHANNEL_BY_ID, id), new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 切换渠道启用/禁用状态
     *
     * @param id      渠道 ID
     * @param enabled 是否启用
     * @return 更新后的渠道信息
     */
    public R<Channel> toggle(Long id, boolean enabled) {
        String path = resolvePath(ApiPathConstants.CHANNEL_TOGGLE, id) + "?enabled=" + enabled;
        return put(path, new ParameterizedTypeReference<R<Channel>>() {});
    }

    /**
     * 获取渠道状态概览
     *
     * @return 渠道状态信息
     */
    public R<ChannelStatusResp> status() {
        return get(ApiPathConstants.CHANNEL_STATUS, new ParameterizedTypeReference<R<ChannelStatusResp>>() {});
    }

    /**
     * 获取指定渠道健康状态
     *
     * @param id 渠道 ID
     * @return 健康状态信息
     */
    public R<ChannelHealthResp> health(Long id) {
        return get(resolvePath(ApiPathConstants.CHANNEL_HEALTH_BY_ID, id), new ParameterizedTypeReference<R<ChannelHealthResp>>() {});
    }

    /**
     * 获取所有渠道健康状态
     *
     * @return 所有渠道健康状态列表
     */
    public R<List<ChannelHealthResp>> healthAll() {
        return get(ApiPathConstants.CHANNEL_HEALTH, new ParameterizedTypeReference<R<List<ChannelHealthResp>>>() {});
    }

    /**
     * 渠道预检验证
     *
     * @param request 预检请求参数
     * @return 验证结果
     */
    public R<ChannelPreflightResp> preflight(ChannelPreflightReq request) {
        return post(ApiPathConstants.CHANNEL_PREFLIGHT, request, new ParameterizedTypeReference<R<ChannelPreflightResp>>() {});
    }
}
