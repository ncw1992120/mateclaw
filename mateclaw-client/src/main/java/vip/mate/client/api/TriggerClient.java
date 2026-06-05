package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.Trigger;
import vip.mate.client.model.request.TriggerIngestReq;
import vip.mate.client.model.response.TriggerIngestResp;

import java.util.List;

/**
 * 触发器客户端
 * <p>
 * 对应服务端 /api/v1/triggers 接口，提供触发器和事件管理功能
 */
public class TriggerClient extends AbstractApiClient {

    public TriggerClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取触发器列表
     *
     * @return 触发器列表
     */
    public R<List<Trigger>> list() {
        return get(ApiPathConstants.TRIGGER, new ParameterizedTypeReference<R<List<Trigger>>>() {});
    }

    /**
     * 获取触发器详情
     *
     * @param id 触发器 ID
     * @return 触发器详情
     */
    public R<Trigger> get(Long id) {
        return get(resolvePath(ApiPathConstants.TRIGGER_BY_ID, id),
                new ParameterizedTypeReference<R<Trigger>>() {});
    }

    /**
     * 创建触发器
     *
     * @param trigger 触发器信息
     * @return 创建的触发器信息
     */
    public R<Trigger> create(Trigger trigger) {
        return post(ApiPathConstants.TRIGGER, trigger,
                new ParameterizedTypeReference<R<Trigger>>() {});
    }

    /**
     * 更新触发器
     *
     * @param id      触发器 ID
     * @param trigger 触发器更新信息
     * @return 更新后的触发器信息
     */
    public R<Trigger> update(Long id, Trigger trigger) {
        return put(resolvePath(ApiPathConstants.TRIGGER_BY_ID, id), trigger,
                new ParameterizedTypeReference<R<Trigger>>() {});
    }

    /**
     * 删除触发器
     *
     * @param id 触发器 ID
     * @return 操作结果
     */
    public R<Void> delete(Long id) {
        return delete(resolvePath(ApiPathConstants.TRIGGER_BY_ID, id),
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 注入触发器事件
     *
     * @param request 事件注入请求
     * @return 注入结果列表
     */
    public R<List<TriggerIngestResp>> ingestEvent(TriggerIngestReq request) {
        return post(ApiPathConstants.TRIGGER_EVENTS, request,
                new ParameterizedTypeReference<R<List<TriggerIngestResp>>>() {});
    }
}
