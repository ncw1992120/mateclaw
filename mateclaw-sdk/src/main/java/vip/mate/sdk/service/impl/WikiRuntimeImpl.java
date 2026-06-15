package vip.mate.sdk.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vip.mate.wiki.WikiProperties;
import vip.mate.wiki.dto.PageCitationWithRaw;
import vip.mate.wiki.dto.PageSearchResult;
import vip.mate.wiki.dto.RelatedPageResult;
import vip.mate.wiki.hotcache.HotCacheUpdateReason;
import vip.mate.wiki.hotcache.HotCacheUpdateScheduler;
import vip.mate.wiki.hotcache.WikiHotCacheService;
import vip.mate.wiki.job.WikiProcessingJobService;
import vip.mate.wiki.job.model.WikiProcessingJobEntity;
import vip.mate.wiki.model.WikiHotCacheEntity;
import vip.mate.wiki.model.WikiKnowledgeBaseEntity;
import vip.mate.wiki.model.WikiPageEntity;
import vip.mate.wiki.model.WikiRawMaterialEntity;
import vip.mate.wiki.model.WikiTransformationEntity;
import vip.mate.wiki.model.WikiTransformationRunEntity;
import vip.mate.wiki.repository.WikiPageCitationMapper;
import vip.mate.wiki.repository.WikiProcessingJobMapper;
import vip.mate.wiki.service.HybridRetriever;
import vip.mate.wiki.service.WikiDirectoryScanService;
import vip.mate.wiki.service.WikiEmbeddingService;
import vip.mate.wiki.service.WikiKnowledgeBaseService;
import vip.mate.wiki.service.WikiPageService;
import vip.mate.wiki.service.WikiProcessingService;
import vip.mate.wiki.service.WikiRawMaterialService;
import vip.mate.wiki.service.WikiRelationService;
import vip.mate.wiki.service.WikiTransformationAggregator;
import vip.mate.wiki.service.WikiTransformationExecutor;
import vip.mate.wiki.service.WikiTransformationService;
import vip.mate.wiki.sse.WikiProgressBus;
import vip.mate.sdk.service.WikiRuntime;

import java.util.List;
import java.util.Optional;

/**
 * Wiki 知识库运行时实现
 * <p>
 * 将所有方法委托给 MateClaw Wiki 内部服务实现，
 * 为宿主应用提供统一的编程式访问入口。
 */
@Service
@RequiredArgsConstructor
public class WikiRuntimeImpl implements WikiRuntime {

    /** 知识库管理服务 */
    private final WikiKnowledgeBaseService kbService;

    /** 原始材料服务 */
    private final WikiRawMaterialService rawService;

    /** 页面管理服务 */
    private final WikiPageService pageService;

    /** 处理服务 */
    private final WikiProcessingService processingService;

    /** 目录扫描服务 */
    private final WikiDirectoryScanService scanService;

    /** Wiki 配置属性 */
    private final WikiProperties properties;

    /** SSE 进度事件总线 */
    private final WikiProgressBus progressBus;

    /** 转换模板服务 */
    private final WikiTransformationService transformationService;

    /** 转换执行器 */
    private final WikiTransformationExecutor transformationExecutor;

    /** 转换聚合器 */
    private final WikiTransformationAggregator transformationAggregator;

    /** 热缓存服务 */
    private final WikiHotCacheService hotCacheService;

    /** 热缓存更新调度器 */
    private final HotCacheUpdateScheduler hotCacheScheduler;

    /** 关系检索服务 */
    private final WikiRelationService relationService;

    /** 处理任务服务 */
    private final WikiProcessingJobService jobService;

    /** 处理任务 Mapper */
    private final WikiProcessingJobMapper jobMapper;

    /** 页面引用 Mapper */
    private final WikiPageCitationMapper citationMapper;

    /** 嵌入服务 */
    private final WikiEmbeddingService embeddingService;

    /** 混合检索器 */
    private final HybridRetriever hybridRetriever;

    // ==================== 知识库管理 ====================

    /**
     * 按工作区列出知识库
     */
    @Override
    public List<WikiKnowledgeBaseEntity> listKBsByWorkspace(Long workspaceId) {
        return kbService.listByWorkspace(workspaceId);
    }

    /**
     * 按 Agent 列出知识库
     */
    @Override
    public List<WikiKnowledgeBaseEntity> listKBsByAgent(Long agentId) {
        return kbService.listByAgentId(agentId);
    }

    /**
     * 获取知识库详情
     */
    @Override
    public WikiKnowledgeBaseEntity getKB(Long id) {
        return kbService.getById(id);
    }

    /**
     * 创建知识库
     */
    @Override
    public WikiKnowledgeBaseEntity createKB(String name, String description, Long agentId, Long workspaceId) {
        return kbService.create(name, description, agentId, workspaceId);
    }

    /**
     * 更新知识库（名称、描述）
     */
    @Override
    public WikiKnowledgeBaseEntity updateKB(Long id, String name, String description) {
        return kbService.update(id, name, description);
    }

    /**
     * 更新知识库绑定的向量模型
     */
    @Override
    public void updateKBEmbeddingModelId(Long id, Long embeddingModelId) {
        kbService.updateEmbeddingModelId(id, embeddingModelId);
    }

    /**
     * 级联删除知识库
     */
    @Override
    public WikiKnowledgeBaseService.CascadeDeleteResult deleteKB(Long id) {
        return kbService.delete(id);
    }

    /**
     * 获取知识库配置
     */
    @Override
    public String getKBConfig(Long id) {
        WikiKnowledgeBaseEntity kb = kbService.getById(id);
        return kb != null ? kb.getConfigContent() : null;
    }

    /**
     * 更新知识库配置
     */
    @Override
    public void updateKBConfig(Long id, String configContent) {
        kbService.updateConfig(id, configContent);
    }

    /**
     * 更新知识库关联目录
     */
    @Override
    public void updateKBSourceDirectory(Long id, String path) {
        kbService.updateSourceDirectory(id, path);
    }

    /**
     * 递减原始材料计数
     */
    @Override
    public void decrementRawCount(Long kbId) {
        kbService.decrementRawCount(kbId);
    }

    /**
     * 设置页面计数
     */
    @Override
    public void setPageCount(Long kbId, int count) {
        kbService.setPageCount(kbId, count);
    }

    // ==================== 原始材料 ====================

    /**
     * 列出知识库下的原始材料
     */
    @Override
    public List<WikiRawMaterialEntity> listRawMaterials(Long kbId) {
        return rawService.listByKbId(kbId);
    }

    /**
     * 获取原始材料详情
     */
    @Override
    public WikiRawMaterialEntity getRawMaterial(Long id) {
        return rawService.getById(id);
    }

    /**
     * 添加文本材料
     */
    @Override
    public WikiRawMaterialEntity addTextRaw(Long kbId, String title, String content) {
        return rawService.addText(kbId, title, content);
    }

    /**
     * 添加文件材料
     */
    @Override
    public WikiRawMaterialEntity addFileRaw(Long kbId, String title, String sourceType, String mimeType, String sourcePath, long fileSize) {
        return rawService.addFile(kbId, title, sourceType, mimeType, sourcePath, fileSize);
    }

    /**
     * 删除原始材料
     */
    @Override
    public void deleteRawMaterial(Long rawId) {
        rawService.delete(rawId);
    }

    /**
     * 设置上次处理哈希（用于强制重处理）
     */
    @Override
    public void setRawLastProcessedHash(Long rawId, String hash) {
        rawService.setLastProcessedHash(rawId, hash);
    }

    /**
     * 重新处理材料
     */
    @Override
    public void reprocessRaw(Long rawId) {
        rawService.reprocess(rawId);
    }

    /**
     * 请求取消处理
     */
    @Override
    public boolean requestCancelRaw(Long rawId) {
        return rawService.requestCancel(rawId);
    }

    // ==================== 页面管理 ====================

    /**
     * 列出知识库页面（不含内容）
     */
    @Override
    public List<WikiPageEntity> listPages(Long kbId) {
        return pageService.listByKbId(kbId);
    }

    /**
     * 按源材料列出页面
     */
    @Override
    public List<WikiPageEntity> listPagesByRawId(Long kbId, Long rawId) {
        return pageService.listBySourceRawId(kbId, rawId);
    }

    /**
     * 列出知识库页面（含内容）
     */
    @Override
    public List<WikiPageEntity> listPagesWithContent(Long kbId) {
        return pageService.listByKbIdWithContent(kbId);
    }

    /**
     * 按 slug 获取页面
     */
    @Override
    public WikiPageEntity getPageBySlug(Long kbId, String slug) {
        return pageService.getBySlug(kbId, slug);
    }

    /**
     * 手动更新页面
     */
    @Override
    public WikiPageEntity updatePageManually(Long kbId, String slug, String content, String summary) {
        return pageService.updatePageManually(kbId, slug, content, summary);
    }

    /**
     * 删除页面
     */
    @Override
    public void deletePage(Long kbId, String slug) {
        pageService.delete(kbId, slug);
    }

    /**
     * 批量删除页面
     */
    @Override
    public int batchDeletePages(Long kbId, List<String> slugs) {
        return pageService.batchDelete(kbId, slugs);
    }

    /**
     * 获取反向链接
     */
    @Override
    public List<WikiPageEntity> getBacklinks(Long kbId, String slug) {
        return pageService.getBacklinks(kbId, slug);
    }

    /**
     * 列出归档页面
     */
    @Override
    public List<WikiPageEntity> listArchivedPages(Long kbId) {
        return pageService.listArchivedByKbId(kbId);
    }

    /**
     * 设置归档状态
     */
    @Override
    public boolean setPageArchived(Long kbId, String slug, boolean archive) {
        return pageService.setArchived(kbId, slug, archive);
    }

    /**
     * 统计页面数
     */
    @Override
    public int countPages(Long kbId) {
        return pageService.countByKbId(kbId);
    }

    /**
     * 按源材料统计页面数
     */
    @Override
    public int countPagesByRawId(Long kbId, Long rawId) {
        return pageService.countBySourceRawId(kbId, rawId);
    }

    // ==================== 处理 ====================

    /**
     * 触发知识库处理
     */
    @Override
    public int processKB(Long kbId, boolean force) {
        return processingService.processKB(kbId, force);
    }

    /**
     * 扫描关联目录
     */
    @Override
    public WikiDirectoryScanService.ScanResult scanDirectory(Long kbId) {
        return scanService.scan(kbId);
    }

    // ==================== 转换模板 ====================

    /**
     * 列出知识库的转换模板
     */
    @Override
    public List<WikiTransformationEntity> listTransformations(Long kbId, Long workspaceId) {
        return transformationService.listForKb(kbId, workspaceId);
    }

    /**
     * 获取转换模板详情
     */
    @Override
    public WikiTransformationEntity getTransformation(Long id) {
        return transformationService.getById(id);
    }

    /**
     * 创建转换模板
     */
    @Override
    public WikiTransformationEntity createTransformation(WikiTransformationEntity input) {
        return transformationService.create(input);
    }

    /**
     * 更新转换模板
     */
    @Override
    public WikiTransformationEntity updateTransformation(Long id, WikiTransformationEntity patch) {
        return transformationService.update(id, patch);
    }

    /**
     * 删除转换模板
     */
    @Override
    public void deleteTransformation(Long id) {
        transformationService.delete(id);
    }

    /**
     * 同步对原始材料运行转换
     */
    @Override
    public WikiTransformationRunEntity runTransformationOnRawSync(WikiTransformationEntity transformation, Long rawId, String triggeredBy) {
        return transformationExecutor.runOnRawSync(transformation, rawId, triggeredBy);
    }

    /**
     * 异步对原始材料运行转换
     */
    @Override
    public void runTransformationOnRawAsync(WikiTransformationEntity transformation, Long rawId, String triggeredBy) {
        transformationExecutor.runOnRawAsync(transformation, rawId, triggeredBy);
    }

    /**
     * 同步对页面运行转换
     */
    @Override
    public WikiTransformationRunEntity runTransformationOnPageSync(WikiTransformationEntity transformation, Long pageId, String triggeredBy) {
        return transformationExecutor.runOnPageSync(transformation, pageId, triggeredBy);
    }

    /**
     * 异步对页面运行转换
     */
    @Override
    public void runTransformationOnPageAsync(WikiTransformationEntity transformation, Long pageId, String triggeredBy) {
        transformationExecutor.runOnPageAsync(transformation, pageId, triggeredBy);
    }

    /**
     * 聚合转换结果
     */
    @Override
    public WikiTransformationAggregator.Result aggregateTransformation(WikiTransformationEntity template, Long kbId, String triggeredBy) {
        return transformationAggregator.aggregate(template, kbId, triggeredBy);
    }

    /**
     * 列出转换运行记录
     */
    @Override
    public List<WikiTransformationRunEntity> listTransformationRuns(Long transformationId, int limit) {
        return transformationService.listRunsByTransformation(transformationId, limit);
    }

    /**
     * 取消转换运行
     */
    @Override
    public boolean cancelTransformationRun(Long runId) {
        return transformationExecutor.cancelRun(runId);
    }

    /**
     * 保存运行为页面
     */
    @Override
    public WikiPageEntity saveRunAsPage(Long runId) {
        return transformationExecutor.manualSaveRunAsPage(runId);
    }

    /**
     * 删除运行记录
     */
    @Override
    public void deleteTransformationRun(Long runId) {
        transformationService.deleteRun(runId);
    }

    // ==================== 热缓存 ====================

    /**
     * 获取热缓存
     */
    @Override
    public Optional<WikiHotCacheEntity> getHotCache(Long kbId) {
        return hotCacheService.findByKb(kbId);
    }

    /**
     * 触发热缓存重建
     */
    @Override
    public void regenerateHotCache(Long kbId, HotCacheUpdateReason reason) {
        hotCacheScheduler.scheduleRebuild(kbId, reason);
    }

    /**
     * 重置热缓存
     */
    @Override
    public void resetHotCache(Long kbId) {
        hotCacheService.findByKb(kbId).ifPresent(cache -> hotCacheService.softDelete(cache.getId()));
    }

    // ==================== 关系与引用 ====================

    /**
     * 获取相关页面
     */
    @Override
    public List<RelatedPageResult> getRelatedPages(Long kbId, String slug, int topK) {
        return relationService.relatedPages(kbId, slug, topK);
    }

    /**
     * 获取页面引用来源
     */
    @Override
    public List<PageCitationWithRaw> getPageCitations(Long pageId) {
        return citationMapper.listWithRawByPageId(pageId);
    }

    // ==================== 任务 ====================

    /**
     * 获取最新原始材料任务
     */
    @Override
    public Optional<WikiProcessingJobEntity> getLatestJobByRawId(Long rawId) {
        return jobMapper.findLatestByRawId(rawId);
    }

    /**
     * 列出队列中的任务
     */
    @Override
    public List<WikiProcessingJobEntity> listQueuedJobs(Long kbId, int limit) {
        return jobMapper.listQueued(kbId, limit);
    }

    /**
     * 列出知识库所有任务
     */
    @Override
    public List<WikiProcessingJobEntity> listJobsByKbId(Long kbId, int limit) {
        return jobMapper.listByKbId(kbId, limit);
    }

    /**
     * 创建轻量丰富任务
     */
    @Override
    public WikiProcessingJobEntity createLightEnrichJob(Long kbId, Long rawId) {
        return jobService.createLightEnrich(kbId, rawId);
    }

    /**
     * 创建本地修复任务
     */
    @Override
    public WikiProcessingJobEntity createLocalRepairJob(Long kbId, Long rawId, Long targetPageId) {
        return jobService.createLocalRepair(kbId, rawId, targetPageId);
    }

    // ==================== 向量与搜索 ====================

    /**
     * 获取嵌入漂移状态
     */
    @Override
    public WikiEmbeddingService.EmbeddingDrift getEmbeddingDrift(Long kbId) {
        return embeddingService.describeDrift(kbId);
    }

    /**
     * 混合搜索
     */
    @Override
    public List<PageSearchResult> search(Long kbId, String query, String mode, int topK) {
        return hybridRetriever.search(kbId, query, mode, topK);
    }

    // ==================== SSE 进度 ====================

    /**
     * 订阅处理进度
     */
    @Override
    public void subscribeProgress(Long kbId, SseEmitter emitter) {
        progressBus.subscribe(kbId, emitter);
    }

    /**
     * 取消订阅
     */
    @Override
    public void unsubscribeProgress(Long kbId, SseEmitter emitter) {
        progressBus.unsubscribe(kbId, emitter);
    }

    /**
     * 广播进度事件
     */
    @Override
    public void broadcastProgress(Long kbId, String eventName, Object data) {
        progressBus.broadcast(kbId, eventName, data);
    }

    // ==================== 配置 ====================

    /**
     * 获取上传目录路径
     */
    @Override
    public String getUploadDir() {
        return properties.getUploadDir();
    }
}
