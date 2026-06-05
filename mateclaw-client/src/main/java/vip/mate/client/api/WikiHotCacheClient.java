package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.response.WikiHotCacheResp;

/**
 * Wiki 热缓存客户端
 * <p>
 * 对应服务端 /api/v1/wiki/hot-cache 接口，提供热缓存管理功能
 */
public class WikiHotCacheClient extends AbstractApiClient {

    public WikiHotCacheClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取热缓存
     *
     * @param kbId 知识库 ID
     * @return 热缓存信息
     */
    public R<WikiHotCacheResp> get(Long kbId) {
        return get(resolvePath(ApiPathConstants.WIKI_HOT_CACHE_BY_ID, kbId),
                new ParameterizedTypeReference<R<WikiHotCacheResp>>() {});
    }

    /**
     * 重新生成热缓存
     *
     * @param kbId 知识库 ID
     * @return 操作结果
     */
    public R<Void> regenerate(Long kbId) {
        return post(resolvePath(ApiPathConstants.WIKI_HOT_CACHE_REGENERATE, kbId), null,
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 重置热缓存
     *
     * @param kbId 知识库 ID
     * @return 操作结果
     */
    public R<Void> reset(Long kbId) {
        return delete(resolvePath(ApiPathConstants.WIKI_HOT_CACHE_BY_ID, kbId),
                new ParameterizedTypeReference<R<Void>>() {});
    }
}
