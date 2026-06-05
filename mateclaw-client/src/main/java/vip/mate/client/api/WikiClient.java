package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.WikiKnowledgeBase;
import vip.mate.client.model.request.WikiConfigReq;
import vip.mate.client.model.request.WikiPageUpdateReq;
import vip.mate.client.model.response.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wiki 知识库客户端
 * <p>
 * 对应服务端 /api/v1/wiki 接口，提供知识库、原始材料、页面等管理功能
 */
public class WikiClient extends AbstractApiClient {

    public WikiClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    // ==================== 知识库管理 ====================

    /**
     * 获取知识库列表
     *
     * @return 知识库列表
     */
    public R<List<WikiKnowledgeBase>> listKBs() {
        return get(ApiPathConstants.WIKI_KB, new ParameterizedTypeReference<R<List<WikiKnowledgeBase>>>() {});
    }

    /**
     * 获取知识库详情
     *
     * @param id 知识库 ID
     * @return 知识库详情
     */
    public R<WikiKnowledgeBase> getKB(Long id) {
        return get(resolvePath(ApiPathConstants.WIKI_KB_BY_ID, id),
                new ParameterizedTypeReference<R<WikiKnowledgeBase>>() {});
    }

    /**
     * 按 Agent 获取知识库
     *
     * @param agentId Agent ID
     * @return 知识库列表
     */
    public R<List<WikiKnowledgeBase>> listKBsByAgent(Long agentId) {
        return get(resolvePath(ApiPathConstants.WIKI_KB_BY_AGENT, agentId),
                new ParameterizedTypeReference<R<List<WikiKnowledgeBase>>>() {});
    }

    /**
     * 创建知识库
     *
     * @param kb 知识库信息
     * @return 创建的知识库信息
     */
    public R<WikiKnowledgeBase> createKB(WikiKnowledgeBase kb) {
        return post(ApiPathConstants.WIKI_KB, kb,
                new ParameterizedTypeReference<R<WikiKnowledgeBase>>() {});
    }

    /**
     * 更新知识库
     *
     * @param id 知识库 ID
     * @param kb 知识库更新信息
     * @return 更新后的知识库信息
     */
    public R<WikiKnowledgeBase> updateKB(Long id, WikiKnowledgeBase kb) {
        return put(resolvePath(ApiPathConstants.WIKI_KB_BY_ID, id), kb,
                new ParameterizedTypeReference<R<WikiKnowledgeBase>>() {});
    }

    /**
     * 删除知识库
     *
     * @param id 知识库 ID
     * @return 操作结果
     */
    public R<Void> deleteKB(Long id) {
        return delete(resolvePath(ApiPathConstants.WIKI_KB_BY_ID, id),
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 获取知识库配置
     *
     * @param id 知识库 ID
     * @return 知识库配置
     */
    public R<WikiConfigReq> getConfig(Long id) {
        return get(resolvePath(ApiPathConstants.WIKI_KB_CONFIG, id),
                new ParameterizedTypeReference<R<WikiConfigReq>>() {});
    }

    /**
     * 更新知识库配置
     *
     * @param id     知识库 ID
     * @param config 配置内容
     * @return 操作结果
     */
    public R<Void> updateConfig(Long id, WikiConfigReq config) {
        return put(resolvePath(ApiPathConstants.WIKI_KB_CONFIG, id), config,
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 设置知识库关联目录
     *
     * @param id   知识库 ID
     * @param path 目录路径
     * @return 操作结果
     */
    public R<Void> setSourceDirectory(Long id, String path) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("path", path);
        return put(resolvePath(ApiPathConstants.WIKI_KB_SOURCE_DIRECTORY, id), body,
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 扫描关联目录导入文件
     *
     * @param id 知识库 ID
     * @return 扫描结果
     */
    public R<DirectoryScanResp> scanDirectory(Long id) {
        return post(resolvePath(ApiPathConstants.WIKI_KB_SCAN, id), null,
                new ParameterizedTypeReference<R<DirectoryScanResp>>() {});
    }

    // ==================== 原始材料管理 ====================

    /**
     * 获取原始材料列表
     *
     * @param kbId 知识库 ID
     * @return 原始材料列表
     */
    public R<List<WikiRawMaterialResp>> listRaw(Long kbId) {
        return get(resolvePath(ApiPathConstants.WIKI_KB_RAW, kbId),
                new ParameterizedTypeReference<R<List<WikiRawMaterialResp>>>() {});
    }

    /**
     * 添加文本材料
     *
     * @param kbId    知识库 ID
     * @param title   标题
     * @param content 内容
     * @return 创建的原始材料信息
     */
    public R<WikiRawMaterialResp> addRawText(Long kbId, String title, String content) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("content", content);
        return post(resolvePath(ApiPathConstants.WIKI_KB_RAW_TEXT, kbId), body,
                new ParameterizedTypeReference<R<WikiRawMaterialResp>>() {});
    }

    /**
     * 删除原始材料
     *
     * @param kbId  知识库 ID
     * @param rawId 原始材料 ID
     * @return 操作结果
     */
    public R<Void> deleteRaw(Long kbId, Long rawId) {
        return delete(resolvePath(ApiPathConstants.WIKI_KB_RAW_BY_ID, kbId, rawId),
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 重新处理原始材料
     *
     * @param kbId  知识库 ID
     * @param rawId 原始材料 ID
     * @param force 是否强制
     * @return 操作结果
     */
    public R<Void> reprocessRaw(Long kbId, Long rawId, Boolean force) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (force != null) {
            params.put("force", force);
        }
        return post(buildUrl(resolvePath(ApiPathConstants.WIKI_KB_RAW_REPROCESS, kbId, rawId), params), null,
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 取消原始材料处理
     *
     * @param kbId  知识库 ID
     * @param rawId 原始材料 ID
     * @return 操作结果
     */
    public R<Void> cancelRaw(Long kbId, Long rawId) {
        return post(resolvePath(ApiPathConstants.WIKI_KB_RAW_CANCEL, kbId, rawId), null,
                new ParameterizedTypeReference<R<Void>>() {});
    }

    // ==================== 页面管理 ====================

    /**
     * 获取页面列表
     *
     * @param kbId 知识库 ID
     * @param rawId 原始材料 ID（可选）
     * @return 页面列表
     */
    public R<List<WikiPageResp>> listPages(Long kbId, Long rawId) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (rawId != null) {
            params.put("rawId", rawId);
        }
        return get(resolvePath(ApiPathConstants.WIKI_KB_PAGES, kbId), params,
                new ParameterizedTypeReference<R<List<WikiPageResp>>>() {});
    }

    /**
     * 获取页面内容
     *
     * @param kbId 知识库 ID
     * @param slug 页面 slug
     * @return 页面内容
     */
    public R<WikiPageResp> getPage(Long kbId, String slug) {
        return get(resolvePath(ApiPathConstants.WIKI_KB_PAGE_BY_SLUG, kbId, slug),
                new ParameterizedTypeReference<R<WikiPageResp>>() {});
    }

    /**
     * 更新页面
     *
     * @param kbId    知识库 ID
     * @param slug    页面 slug
     * @param request 更新请求
     * @return 更新后的页面信息
     */
    public R<WikiPageResp> updatePage(Long kbId, String slug, WikiPageUpdateReq request) {
        return put(resolvePath(ApiPathConstants.WIKI_KB_PAGE_BY_SLUG, kbId, slug), request,
                new ParameterizedTypeReference<R<WikiPageResp>>() {});
    }

    /**
     * 删除页面
     *
     * @param kbId 知识库 ID
     * @param slug 页面 slug
     * @return 操作结果
     */
    public R<Void> deletePage(Long kbId, String slug) {
        return delete(resolvePath(ApiPathConstants.WIKI_KB_PAGE_BY_SLUG, kbId, slug),
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 批量删除页面
     *
     * @param kbId 知识库 ID
     * @param slugs 页面 slug 列表
     * @return 删除数量
     */
    public R<Integer> batchDeletePages(Long kbId, List<String> slugs) {
        return delete(resolvePath(ApiPathConstants.WIKI_KB_PAGES_BATCH, kbId), slugs,
                new ParameterizedTypeReference<R<Integer>>() {});
    }

    /**
     * 获取反向链接
     *
     * @param kbId 知识库 ID
     * @param slug 页面 slug
     * @return 反向链接列表
     */
    public R<List<WikiPageResp>> getBacklinks(Long kbId, String slug) {
        return get(resolvePath(ApiPathConstants.WIKI_KB_PAGE_BACKLINKS, kbId, slug),
                new ParameterizedTypeReference<R<List<WikiPageResp>>>() {});
    }

    /**
     * 获取归档页面列表
     *
     * @param kbId 知识库 ID
     * @return 归档页面列表
     */
    public R<List<WikiPageResp>> listArchivedPages(Long kbId) {
        return get(resolvePath(ApiPathConstants.WIKI_KB_PAGES_ARCHIVED, kbId),
                new ParameterizedTypeReference<R<List<WikiPageResp>>>() {});
    }

    /**
     * 归档页面
     *
     * @param kbId 知识库 ID
     * @param slug 页面 slug
     * @return 操作结果
     */
    public R<PageArchiveResp> archivePage(Long kbId, String slug) {
        return post(resolvePath(ApiPathConstants.WIKI_KB_PAGE_ARCHIVE, kbId, slug), null,
                new ParameterizedTypeReference<R<PageArchiveResp>>() {});
    }

    /**
     * 取消归档页面
     *
     * @param kbId 知识库 ID
     * @param slug 页面 slug
     * @return 操作结果
     */
    public R<PageArchiveResp> unarchivePage(Long kbId, String slug) {
        return post(resolvePath(ApiPathConstants.WIKI_KB_PAGE_UNARCHIVE, kbId, slug), null,
                new ParameterizedTypeReference<R<PageArchiveResp>>() {});
    }

    // ==================== 处理管理 ====================

    /**
     * 触发知识库处理
     *
     * @param kbId  知识库 ID
     * @param force 是否强制
     * @return 处理结果
     */
    public R<KbProcessResp> processKB(Long kbId, Boolean force) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (force != null) {
            params.put("force", force);
        }
        return post(buildUrl(resolvePath(ApiPathConstants.WIKI_KB_PROCESS, kbId), params), null,
                new ParameterizedTypeReference<R<KbProcessResp>>() {});
    }

    /**
     * 获取处理状态
     *
     * @param kbId 知识库 ID
     * @return 处理状态
     */
    public R<KbProcessingStatusResp> getProcessingStatus(Long kbId) {
        return get(resolvePath(ApiPathConstants.WIKI_KB_PROCESSING_STATUS, kbId),
                new ParameterizedTypeReference<R<KbProcessingStatusResp>>() {});
    }

    /**
     * 订阅处理进度 SSE 流
     *
     * @param kbId     知识库 ID
     * @param callback SSE 事件回调
     */
    public void subscribeProgress(Long kbId, SseStreamCallback callback) {
        getForSseStream(resolvePath(ApiPathConstants.WIKI_KB_PROGRESS, kbId), callback);
    }
}
