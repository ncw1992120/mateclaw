package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.request.WikiSearchPreviewReq;
import vip.mate.client.model.response.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wiki 关联客户端
 * <p>
 * 对应服务端 /api/v1/wiki 关联接口，提供知识库关联页面、引用、统计等功能
 */
public class WikiRelationClient extends AbstractApiClient {

    public WikiRelationClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取关联页面
     *
     * @param kbId 知识库 ID
     * @param slug 页面 slug
     * @param topK 返回数量（默认 5）
     * @return 关联页面列表
     */
    public List<WikiPageResp> relatedPages(Long kbId, String slug, Integer topK) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (topK != null) {
            params.put("topK", topK);
        }
        return get(resolvePath(ApiPathConstants.WIKI_RELATED_PAGES, kbId, slug), params,
                new ParameterizedTypeReference<List<WikiPageResp>>() {});
    }

    /**
     * 获取关联解释
     *
     * @param kbId   知识库 ID
     * @param slugA  页面 A slug
     * @param slugB  页面 B slug
     * @return 关联解释
     */
    public RelationExplanationResp explainRelation(Long kbId, String slugA, String slugB) {
        return get(resolvePath(ApiPathConstants.WIKI_RELATION_EXPLAIN, kbId, slugA, slugB),
                new ParameterizedTypeReference<RelationExplanationResp>() {});
    }

    /**
     * 按原始材料获取页面列表
     *
     * @param rawId 原始材料 ID
     * @return 页面列表
     */
    public List<WikiPageResp> pagesByRawId(Long rawId) {
        return get(resolvePath(ApiPathConstants.WIKI_RAW_PAGES, rawId),
                new ParameterizedTypeReference<List<WikiPageResp>>() {});
    }

    /**
     * 按分块获取页面列表
     *
     * @param chunkId 分块 ID
     * @return 页面列表
     */
    public List<WikiPageResp> pagesByChunkId(Long chunkId) {
        return get(resolvePath(ApiPathConstants.WIKI_CHUNK_PAGES, chunkId),
                new ParameterizedTypeReference<List<WikiPageResp>>() {});
    }

    /**
     * 获取页面引用
     *
     * @param kbId   知识库 ID
     * @param pageId 页面 ID
     * @return 引用列表
     */
    public List<WikiPageResp> pageCitations(Long kbId, Long pageId) {
        return get(resolvePath(ApiPathConstants.WIKI_PAGE_CITATIONS, kbId, pageId),
                new ParameterizedTypeReference<List<WikiPageResp>>() {});
    }

    /**
     * 获取处理任务
     *
     * @param kbId  知识库 ID
     * @param rawId 原始材料 ID（可选）
     * @return 任务列表
     */
    public List<WikiProcessingJobResp> getJobs(Long kbId, Long rawId) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (rawId != null) {
            params.put("rawId", rawId);
        }
        return get(resolvePath(ApiPathConstants.WIKI_KB_JOBS, kbId), params,
                new ParameterizedTypeReference<List<WikiProcessingJobResp>>() {});
    }

    /**
     * 获取知识库统计
     *
     * @param kbId 知识库 ID
     * @return 统计信息
     */
    public KbStatsResp kbStats(Long kbId) {
        return get(resolvePath(ApiPathConstants.WIKI_KB_STATS, kbId),
                new ParameterizedTypeReference<KbStatsResp>() {});
    }

    /**
     * 增强页面
     *
     * @param kbId 知识库 ID
     * @param slug 页面 slug
     * @return 增强结果
     */
    public PageJobResp enrichPage(Long kbId, String slug) {
        return post(resolvePath(ApiPathConstants.WIKI_PAGE_ENRICH, kbId, slug), null,
                new ParameterizedTypeReference<PageJobResp>() {});
    }

    /**
     * 修复页面
     *
     * @param kbId 知识库 ID
     * @param slug 页面 slug
     * @return 修复结果
     */
    public PageJobResp repairPage(Long kbId, String slug) {
        return post(resolvePath(ApiPathConstants.WIKI_PAGE_REPAIR, kbId, slug), null,
                new ParameterizedTypeReference<PageJobResp>() {});
    }

    /**
     * 搜索预览
     *
     * @param kbId    知识库 ID
     * @param request 搜索请求参数
     * @return 搜索结果列表
     */
    public List<PageSearchResp> searchPreview(Long kbId, WikiSearchPreviewReq request) {
        return post(resolvePath(ApiPathConstants.WIKI_SEARCH_PREVIEW, kbId), request,
                new ParameterizedTypeReference<List<PageSearchResp>>() {});
    }
}
