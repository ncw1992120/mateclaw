package vip.mate.dataagent.service;

import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vip.mate.wiki.dto.PageCitationWithRaw;
import vip.mate.wiki.dto.PageSearchResult;
import vip.mate.wiki.dto.RelatedPageResult;
import vip.mate.wiki.job.model.WikiProcessingJobEntity;
import vip.mate.wiki.model.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 业务知识库管理服务接口
 * <p>
 * 封装 Wiki 知识库相关的业务逻辑，供 Controller 调用。
 * 所有 Wiki 能力通过 WikiRuntime（mateclaw-sdk 封装）调用。
 */
public interface DataAgentWikiService {

    // ==================== Knowledge Base ====================

    /**
     * 获取知识库列表（含实时页面计数）
     *
     * @param workspaceId 工作区 ID
     * @return 知识库实体列表
     */
    List<WikiKnowledgeBaseEntity> listKBs(Long workspaceId);

    /**
     * 按 Agent 获取知识库（含实时页面计数）
     *
     * @param agentId     Agent ID
     * @param workspaceId 工作区 ID
     * @return 知识库实体列表
     */
    List<WikiKnowledgeBaseEntity> listKBsByAgent(Long agentId, Long workspaceId);

    /**
     * 获取知识库详情
     *
     * @param id 知识库 ID
     * @return 知识库实体，不存在时返回 null
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
     * 更新知识库
     *
     * @param id   知识库 ID
     * @param body 更新参数
     * @return 更新后的知识库实体
     */
    WikiKnowledgeBaseEntity updateKB(Long id, Map<String, Object> body);

    /**
     * 删除知识库，返回级联删除统计
     *
     * @param id 知识库 ID
     * @return 级联删除统计信息
     */
    Map<String, Object> deleteKB(Long id);

    /**
     * 获取知识库配置
     *
     * @param id 知识库 ID
     * @return 配置内容，不存在时返回 null
     */
    String getKBConfig(Long id);

    /**
     * 更新知识库配置
     *
     * @param id      知识库 ID
     * @param content 配置内容
     */
    void updateKBConfig(Long id, String content);

    // ==================== Directory Scan ====================

    /**
     * 设置关联目录
     *
     * @param id   知识库 ID
     * @param path 目录路径
     */
    void setSourceDirectory(Long id, String path);

    /**
     * 扫描关联目录
     *
     * @param id 知识库 ID
     * @return 扫描结果
     */
    Map<String, Object> scanDirectory(Long id);

    // ==================== Raw Materials ====================

    /**
     * 原始材料列表（含页面数）
     *
     * @param kbId 知识库 ID
     * @return 原始材料列表（Map 形式，含 pageCount）
     */
    List<Map<String, Object>> listRawMaterials(Long kbId);

    /**
     * 添加文本材料
     *
     * @param kbId    知识库 ID
     * @param title   标题
     * @param content 文本内容
     * @return 创建后的原始材料实体
     */
    WikiRawMaterialEntity addRawText(Long kbId, String title, String content);

    /**
     * 上传文件材料
     *
     * @param kbId 知识库 ID
     * @param file 上传的文件
     * @return 创建后的原始材料实体
     * @throws IOException 文件读写异常
     */
    WikiRawMaterialEntity uploadRaw(Long kbId, MultipartFile file) throws IOException;

    /**
     * 删除原始材料
     *
     * @param kbId  知识库 ID
     * @param rawId 原始材料 ID
     */
    void deleteRaw(Long kbId, Long rawId);

    /**
     * 重新处理材料
     *
     * @param kbId  知识库 ID
     * @param rawId 原始材料 ID
     * @param force 是否强制重处理
     */
    void reprocessRaw(Long kbId, Long rawId, boolean force);

    /**
     * 取消材料处理
     *
     * @param kbId  知识库 ID
     * @param rawId 原始材料 ID
     */
    void cancelRaw(Long kbId, Long rawId);

    /**
     * 下载原始材料
     *
     * @param kbId  知识库 ID
     * @param rawId 原始材料 ID
     * @return 下载响应
     * @throws IOException 文件读写异常
     */
    ResponseEntity<org.springframework.core.io.Resource> downloadRaw(Long kbId, Long rawId) throws IOException;

    // ==================== Processing ====================

    /**
     * 触发知识库处理
     *
     * @param kbId  知识库 ID
     * @param force 是否强制重处理
     * @return 处理结果
     */
    Map<String, Object> processKB(Long kbId, boolean force);

    /**
     * 获取处理状态
     *
     * @param kbId 知识库 ID
     * @return 处理状态信息
     */
    Map<String, Object> getProcessingStatus(Long kbId);

    /**
     * 订阅处理进度 SSE
     *
     * @param kbId 知识库 ID
     * @return SSE 发射器
     */
    SseEmitter subscribeProgress(Long kbId);

    // ==================== Wiki Pages ====================

    /**
     * 页面列表
     *
     * @param kbId  知识库 ID
     * @param rawId 原始材料 ID（可选过滤）
     * @return 页面实体列表
     */
    List<WikiPageEntity> listPages(Long kbId, Long rawId);

    /**
     * 页面详情
     *
     * @param kbId 知识库 ID
     * @param slug 页面 slug
     * @return 页面实体，不存在时返回 null
     */
    WikiPageEntity getPage(Long kbId, String slug);

    /**
     * 更新页面
     *
     * @param kbId    知识库 ID
     * @param slug    页面 slug
     * @param content 新内容
     * @param summary 新摘要
     * @return 更新后的页面实体
     */
    WikiPageEntity updatePage(Long kbId, String slug, String content, String summary);

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
     * 归档页面
     *
     * @param kbId 知识库 ID
     * @param slug 页面 slug
     * @return 归档结果
     */
    Map<String, Object> archivePage(Long kbId, String slug);

    /**
     * 取消归档
     *
     * @param kbId 知识库 ID
     * @param slug 页面 slug
     * @return 取消归档结果
     */
    Map<String, Object> unarchivePage(Long kbId, String slug);

    // ==================== Transformations ====================

    /**
     * 列出转换模板
     *
     * @param kbId 知识库 ID
     * @return 转换模板实体列表
     */
    List<WikiTransformationEntity> listTransformations(Long kbId);

    /**
     * 创建转换模板
     *
     * @param kbId 知识库 ID
     * @param body 转换模板实体
     * @return 创建后的转换模板实体
     */
    WikiTransformationEntity createTransformation(Long kbId, WikiTransformationEntity body);

    /**
     * 更新转换模板
     *
     * @param id   转换模板 ID
     * @param body 更新内容
     * @return 更新后的转换模板实体
     */
    WikiTransformationEntity updateTransformation(Long id, WikiTransformationEntity body);

    /**
     * 删除转换模板
     *
     * @param id 转换模板 ID
     */
    void deleteTransformation(Long id);

    /**
     * 应用转换
     *
     * @param id   转换模板 ID
     * @param body 请求参数
     * @param sync 是否同步执行
     * @return 转换运行记录（同步时返回，异步时返回 null）
     */
    WikiTransformationRunEntity applyTransformation(Long id, Map<String, Object> body, boolean sync);

    /**
     * 聚合转换
     *
     * @param id   转换模板 ID
     * @param kbId 知识库 ID
     * @return 聚合结果
     */
    Map<String, Object> aggregateTransformation(Long id, Long kbId);

    /**
     * 列出转换运行记录
     *
     * @param id 转换模板 ID
     * @return 转换运行记录列表
     */
    List<WikiTransformationRunEntity> listTransformationRuns(Long id);

    /**
     * 取消转换运行
     *
     * @param runId 运行记录 ID
     * @return 是否成功取消
     */
    boolean cancelTransformationRun(Long runId);

    /**
     * 保存运行结果为页面
     *
     * @param runId 运行记录 ID
     * @return 保存结果
     */
    Map<String, Object> saveRunAsPage(Long runId);

    /**
     * 删除运行记录
     *
     * @param runId 运行记录 ID
     */
    void deleteTransformationRun(Long runId);

    // ==================== Hot Cache ====================

    /**
     * 获取热缓存
     *
     * @param kbId 知识库 ID
     * @return 热缓存实体
     */
    WikiHotCacheEntity getHotCache(Long kbId);

    /**
     * 重新生成热缓存
     *
     * @param kbId 知识库 ID
     */
    void regenerateHotCache(Long kbId);

    /**
     * 重置热缓存
     *
     * @param kbId 知识库 ID
     */
    void resetHotCache(Long kbId);

    // ==================== Relations / Stats / Jobs ====================

    /**
     * 获取相关页面
     *
     * @param kbId 知识库 ID
     * @param slug 种子页面 slug
     * @param topK 返回数量上限
     * @return 相关页面结果列表
     */
    List<RelatedPageResult> getRelatedPages(Long kbId, String slug, int topK);

    /**
     * 获取页面引用来源
     *
     * @param kbId   知识库 ID
     * @param pageId 页面 ID
     * @return 引用来源列表
     */
    List<PageCitationWithRaw> getPageCitations(Long kbId, Long pageId);

    /**
     * 获取处理任务
     *
     * @param kbId  知识库 ID
     * @param rawId 原始材料 ID（可选过滤）
     * @return 处理任务列表
     */
    List<WikiProcessingJobEntity> getJobs(Long kbId, Long rawId);

    /**
     * 知识库统计
     *
     * @param kbId 知识库 ID
     * @return 统计信息
     */
    Map<String, Object> kbStats(Long kbId);

    /**
     * 富化页面
     *
     * @param kbId 知识库 ID
     * @param slug 页面 slug
     * @return 富化结果（含 jobId）
     */
    Map<String, Object> enrichPage(Long kbId, String slug);

    /**
     * 修复页面
     *
     * @param kbId 知识库 ID
     * @param slug 页面 slug
     * @return 修复结果（含 jobId）
     */
    Map<String, Object> repairPage(Long kbId, String slug);

    /**
     * 搜索预览
     *
     * @param kbId 知识库 ID
     * @param body 搜索参数
     * @return 搜索结果列表
     */
    List<PageSearchResult> searchPreview(Long kbId, Map<String, Object> body);
}
