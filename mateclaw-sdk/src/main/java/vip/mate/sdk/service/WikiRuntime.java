package vip.mate.sdk.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vip.mate.wiki.dto.PageCitationWithRaw;
import vip.mate.wiki.dto.PageSearchResult;
import vip.mate.wiki.dto.RelatedPageResult;
import vip.mate.wiki.hotcache.HotCacheUpdateReason;
import vip.mate.wiki.job.model.WikiProcessingJobEntity;
import vip.mate.wiki.model.*;
import vip.mate.wiki.service.WikiDirectoryScanService;
import vip.mate.wiki.service.WikiEmbeddingService;
import vip.mate.wiki.service.WikiKnowledgeBaseService;
import vip.mate.wiki.service.WikiTransformationAggregator;

import java.util.List;
import java.util.Optional;

/**
 * Wiki 知识库运行时接口
 * <p>
 * 提供对 MateClaw Wiki 知识库模块的编程式访问，包括知识库管理、
 * 原始材料管理、页面管理、处理触发、转换模板、热缓存、关系检索等。
 * 宿主应用通过注入此接口即可使用 Wiki 全部能力，
 * 无需直接依赖 mateclaw-server 内部服务实现。
 */
public interface WikiRuntime {

    // ==================== 知识库管理 ====================

    /**
     * 按工作区列出知识库
     *
     * @param workspaceId 工作区 ID
     * @return 知识库实体列表
     */
    List<WikiKnowledgeBaseEntity> listKBsByWorkspace(Long workspaceId);

    /**
     * 按 Agent 列出知识库
     *
     * @param agentId Agent ID
     * @return 知识库实体列表
     */
    List<WikiKnowledgeBaseEntity> listKBsByAgent(Long agentId);

    /**
     * 获取知识库详情
     *
     * @param id 知识库 ID
     * @return 知识库实体
     */
    WikiKnowledgeBaseEntity getKB(Long id);

    /**
     * 创建知识库
     *
     * @param name        知识库名称
     * @param description 知识库描述
     * @param agentId     关联 Agent ID
     * @param workspaceId 工作区 ID
     * @return 创建后的知识库实体
     */
    WikiKnowledgeBaseEntity createKB(String name, String description, Long agentId, Long workspaceId);

    /**
     * 更新知识库（名称、描述）
     *
     * @param id          知识库 ID
     * @param name        新名称
     * @param description 新描述
     * @return 更新后的知识库实体
     */
    WikiKnowledgeBaseEntity updateKB(Long id, String name, String description);

    /**
     * 更新知识库绑定的向量模型
     *
     * @param id               知识库 ID
     * @param embeddingModelId 向量模型 ID，null 表示解绑
     */
    void updateKBEmbeddingModelId(Long id, Long embeddingModelId);

    /**
     * 级联删除知识库
     *
     * @param id 知识库 ID
     * @return 级联删除结果
     */
    WikiKnowledgeBaseService.CascadeDeleteResult deleteKB(Long id);

    /**
     * 获取知识库配置
     *
     * @param id 知识库 ID
     * @return 配置内容
     */
    String getKBConfig(Long id);

    /**
     * 更新知识库配置
     *
     * @param id           知识库 ID
     * @param configContent 配置内容
     */
    void updateKBConfig(Long id, String configContent);

    /**
     * 更新知识库关联目录
     *
     * @param id   知识库 ID
     * @param path 目录路径
     */
    void updateKBSourceDirectory(Long id, String path);

    /**
     * 递减原始材料计数
     *
     * @param kbId 知识库 ID
     */
    void decrementRawCount(Long kbId);

    /**
     * 设置页面计数
     *
     * @param kbId  知识库 ID
     * @param count 页面数
     */
    void setPageCount(Long kbId, int count);

    // ==================== 原始材料 ====================

    /**
     * 列出知识库下的原始材料
     *
     * @param kbId 知识库 ID
     * @return 原始材料实体列表
     */
    List<WikiRawMaterialEntity> listRawMaterials(Long kbId);

    /**
     * 获取原始材料详情
     *
     * @param id 原始材料 ID
     * @return 原始材料实体
     */
    WikiRawMaterialEntity getRawMaterial(Long id);

    /**
     * 添加文本材料
     *
     * @param kbId    知识库 ID
     * @param title   标题
     * @param content 文本内容
     * @return 创建后的原始材料实体
     */
    WikiRawMaterialEntity addTextRaw(Long kbId, String title, String content);

    /**
     * 添加文件材料
     *
     * @param kbId       知识库 ID
     * @param title      标题
     * @param sourceType 来源类型
     * @param mimeType   MIME 类型
     * @param sourcePath 源文件路径
     * @param fileSize   文件大小
     * @return 创建后的原始材料实体
     */
    WikiRawMaterialEntity addFileRaw(Long kbId, String title, String sourceType, String mimeType, String sourcePath, long fileSize);

    /**
     * 删除原始材料
     *
     * @param rawId 原始材料 ID
     */
    void deleteRawMaterial(Long rawId);

    /**
     * 设置上次处理哈希（用于强制重处理）
     *
     * @param rawId 原始材料 ID
     * @param hash  哈希值
     */
    void setRawLastProcessedHash(Long rawId, String hash);

    /**
     * 重新处理材料
     *
     * @param rawId 原始材料 ID
     */
    void reprocessRaw(Long rawId);

    /**
     * 请求取消处理
     *
     * @param rawId 原始材料 ID
     * @return 是否成功设置取消标记
     */
    boolean requestCancelRaw(Long rawId);

    // ==================== 页面管理 ====================

    /**
     * 列出知识库页面（不含内容）
     *
     * @param kbId 知识库 ID
     * @return 页面实体列表
     */
    List<WikiPageEntity> listPages(Long kbId);

    /**
     * 按源材料列出页面
     *
     * @param kbId  知识库 ID
     * @param rawId 原始材料 ID
     * @return 页面实体列表
     */
    List<WikiPageEntity> listPagesByRawId(Long kbId, Long rawId);

    /**
     * 列出知识库页面（含内容）
     *
     * @param kbId 知识库 ID
     * @return 页面实体列表
     */
    List<WikiPageEntity> listPagesWithContent(Long kbId);

    /**
     * 按 slug 获取页面
     *
     * @param kbId 知识库 ID
     * @param slug 页面 slug
     * @return 页面实体
     */
    WikiPageEntity getPageBySlug(Long kbId, String slug);

    /**
     * 手动更新页面
     *
     * @param kbId    知识库 ID
     * @param slug    页面 slug
     * @param content 新内容
     * @param summary 新摘要
     * @return 更新后的页面实体
     */
    WikiPageEntity updatePageManually(Long kbId, String slug, String content, String summary);

    /**
     * 删除页面
     *
     * @param kbId 知识库 ID
     * @param slug 页面 slug
     */
    void deletePage(Long kbId, String slug);

    /**
     * 批量删除页面
     *
     * @param kbId  知识库 ID
     * @param slugs 页面 slug 列表
     * @return 删除数量
     */
    int batchDeletePages(Long kbId, List<String> slugs);

    /**
     * 获取反向链接
     *
     * @param kbId 知识库 ID
     * @param slug 页面 slug
     * @return 反向链接页面列表
     */
    List<WikiPageEntity> getBacklinks(Long kbId, String slug);

    /**
     * 列出归档页面
     *
     * @param kbId 知识库 ID
     * @return 归档页面列表
     */
    List<WikiPageEntity> listArchivedPages(Long kbId);

    /**
     * 设置归档状态
     *
     * @param kbId    知识库 ID
     * @param slug    页面 slug
     * @param archive 是否归档
     * @return 是否成功变更状态
     */
    boolean setPageArchived(Long kbId, String slug, boolean archive);

    /**
     * 统计页面数
     *
     * @param kbId 知识库 ID
     * @return 页面数
     */
    int countPages(Long kbId);

    /**
     * 按源材料统计页面数
     *
     * @param kbId  知识库 ID
     * @param rawId 原始材料 ID
     * @return 页面数
     */
    int countPagesByRawId(Long kbId, Long rawId);

    // ==================== 处理 ====================

    /**
     * 触发知识库处理
     *
     * @param kbId  知识库 ID
     * @param force 是否强制重处理
     * @return 处理的材料数
     */
    int processKB(Long kbId, boolean force);

    /**
     * 扫描关联目录
     *
     * @param kbId 知识库 ID
     * @return 扫描结果
     */
    WikiDirectoryScanService.ScanResult scanDirectory(Long kbId);

    // ==================== 转换模板 ====================

    /**
     * 列出知识库的转换模板
     *
     * @param kbId        知识库 ID
     * @param workspaceId 工作区 ID
     * @return 转换模板实体列表
     */
    List<WikiTransformationEntity> listTransformations(Long kbId, Long workspaceId);

    /**
     * 获取转换模板详情
     *
     * @param id 转换模板 ID
     * @return 转换模板实体
     */
    WikiTransformationEntity getTransformation(Long id);

    /**
     * 创建转换模板
     *
     * @param input 转换模板实体
     * @return 创建后的转换模板实体
     */
    WikiTransformationEntity createTransformation(WikiTransformationEntity input);

    /**
     * 更新转换模板
     *
     * @param id    转换模板 ID
     * @param patch 更新内容
     * @return 更新后的转换模板实体
     */
    WikiTransformationEntity updateTransformation(Long id, WikiTransformationEntity patch);

    /**
     * 删除转换模板
     *
     * @param id 转换模板 ID
     */
    void deleteTransformation(Long id);

    /**
     * 同步对原始材料运行转换
     *
     * @param transformation 转换模板
     * @param rawId          原始材料 ID
     * @param triggeredBy    触发者标识
     * @return 转换运行记录
     */
    WikiTransformationRunEntity runTransformationOnRawSync(WikiTransformationEntity transformation, Long rawId, String triggeredBy);

    /**
     * 异步对原始材料运行转换
     *
     * @param transformation 转换模板
     * @param rawId          原始材料 ID
     * @param triggeredBy    触发者标识
     */
    void runTransformationOnRawAsync(WikiTransformationEntity transformation, Long rawId, String triggeredBy);

    /**
     * 同步对页面运行转换
     *
     * @param transformation 转换模板
     * @param pageId         页面 ID
     * @param triggeredBy    触发者标识
     * @return 转换运行记录
     */
    WikiTransformationRunEntity runTransformationOnPageSync(WikiTransformationEntity transformation, Long pageId, String triggeredBy);

    /**
     * 异步对页面运行转换
     *
     * @param transformation 转换模板
     * @param pageId         页面 ID
     * @param triggeredBy    触发者标识
     */
    void runTransformationOnPageAsync(WikiTransformationEntity transformation, Long pageId, String triggeredBy);

    /**
     * 聚合转换结果
     *
     * @param template    转换模板
     * @param kbId        知识库 ID
     * @param triggeredBy 触发者标识
     * @return 聚合结果
     */
    WikiTransformationAggregator.Result aggregateTransformation(WikiTransformationEntity template, Long kbId, String triggeredBy);

    /**
     * 列出转换运行记录
     *
     * @param transformationId 转换模板 ID
     * @param limit            返回数量上限
     * @return 转换运行记录列表
     */
    List<WikiTransformationRunEntity> listTransformationRuns(Long transformationId, int limit);

    /**
     * 取消转换运行
     *
     * @param runId 运行记录 ID
     * @return 是否成功取消
     */
    boolean cancelTransformationRun(Long runId);

    /**
     * 保存运行为页面
     *
     * @param runId 运行记录 ID
     * @return 保存后的页面实体
     */
    WikiPageEntity saveRunAsPage(Long runId);

    /**
     * 删除运行记录
     *
     * @param runId 运行记录 ID
     */
    void deleteTransformationRun(Long runId);

    // ==================== 热缓存 ====================

    /**
     * 获取热缓存
     *
     * @param kbId 知识库 ID
     * @return 热缓存实体
     */
    Optional<WikiHotCacheEntity> getHotCache(Long kbId);

    /**
     * 触发热缓存重建
     *
     * @param kbId   知识库 ID
     * @param reason 重建原因
     */
    void regenerateHotCache(Long kbId, HotCacheUpdateReason reason);

    /**
     * 重置热缓存
     *
     * @param kbId 知识库 ID
     */
    void resetHotCache(Long kbId);

    // ==================== 关系与引用 ====================

    /**
     * 获取相关页面
     *
     * @param kbId  知识库 ID
     * @param slug  种子页面 slug
     * @param topK  返回数量上限
     * @return 相关页面结果列表
     */
    List<RelatedPageResult> getRelatedPages(Long kbId, String slug, int topK);

    /**
     * 获取页面引用来源
     *
     * @param pageId 页面 ID
     * @return 引用来源列表
     */
    List<PageCitationWithRaw> getPageCitations(Long pageId);

    // ==================== 任务 ====================

    /**
     * 获取最新原始材料任务
     *
     * @param rawId 原始材料 ID
     * @return 最新的处理任务
     */
    Optional<WikiProcessingJobEntity> getLatestJobByRawId(Long rawId);

    /**
     * 列出队列中的任务
     *
     * @param kbId  知识库 ID
     * @param limit 返回数量上限
     * @return 处理任务列表
     */
    List<WikiProcessingJobEntity> listQueuedJobs(Long kbId, int limit);

    /**
     * 列出知识库所有任务
     *
     * @param kbId  知识库 ID
     * @param limit 返回数量上限
     * @return 处理任务列表
     */
    List<WikiProcessingJobEntity> listJobsByKbId(Long kbId, int limit);

    /**
     * 创建轻量丰富任务
     *
     * @param kbId  知识库 ID
     * @param rawId 原始材料 ID
     * @return 创建的处理任务
     */
    WikiProcessingJobEntity createLightEnrichJob(Long kbId, Long rawId);

    /**
     * 创建本地修复任务
     *
     * @param kbId         知识库 ID
     * @param rawId        原始材料 ID
     * @param targetPageId 目标页面 ID
     * @return 创建的处理任务
     */
    WikiProcessingJobEntity createLocalRepairJob(Long kbId, Long rawId, Long targetPageId);

    // ==================== 向量与搜索 ====================

    /**
     * 获取嵌入漂移状态
     *
     * @param kbId 知识库 ID
     * @return 嵌入漂移信息
     */
    WikiEmbeddingService.EmbeddingDrift getEmbeddingDrift(Long kbId);

    /**
     * 混合搜索
     *
     * @param kbId  知识库 ID
     * @param query 查询文本
     * @param mode  搜索模式（keyword / semantic / hybrid）
     * @param topK  返回数量上限
     * @return 搜索结果列表
     */
    List<PageSearchResult> search(Long kbId, String query, String mode, int topK);

    // ==================== SSE 进度 ====================

    /**
     * 订阅处理进度
     *
     * @param kbId    知识库 ID
     * @param emitter SSE 发射器
     */
    void subscribeProgress(Long kbId, SseEmitter emitter);

    /**
     * 取消订阅
     *
     * @param kbId    知识库 ID
     * @param emitter SSE 发射器
     */
    void unsubscribeProgress(Long kbId, SseEmitter emitter);

    /**
     * 广播进度事件
     *
     * @param kbId      知识库 ID
     * @param eventName 事件名称
     * @param data      事件数据
     */
    void broadcastProgress(Long kbId, String eventName, Object data);

    // ==================== 配置 ====================

    /**
     * 获取上传目录路径
     *
     * @return 上传目录路径
     */
    String getUploadDir();
}
