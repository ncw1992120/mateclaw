package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.WikiKnowledgeBase;
import vip.mate.client.model.response.BackfillResp;

/**
 * Wiki 管理客户端
 * <p>
 * 对应服务端 /api/v1/wiki/admin 接口，提供知识库管理操作
 */
public class WikiAdminClient extends AbstractApiClient {

    public WikiAdminClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 重建知识库概览
     *
     * @param kbId 知识库 ID
     * @return 重建结果
     */
    public R<WikiKnowledgeBase> rebuildOverview(Long kbId) {
        return post(resolvePath(ApiPathConstants.WIKI_ADMIN_KB_REBUILD_OVERVIEW, kbId), null,
                new ParameterizedTypeReference<R<WikiKnowledgeBase>>() {});
    }

    /**
     * 回填 Token 数
     *
     * @return 回填结果
     */
    public BackfillResp backfillTokens() {
        return post(ApiPathConstants.WIKI_ADMIN_BACKFILL_TOKENS, null,
                new ParameterizedTypeReference<BackfillResp>() {});
    }
}
