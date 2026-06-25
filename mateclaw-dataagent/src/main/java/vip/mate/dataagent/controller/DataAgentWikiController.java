package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vip.mate.common.result.R;
import vip.mate.dataagent.service.DataAgentWikiService;
import vip.mate.wiki.dto.PageCitationWithRaw;
import vip.mate.wiki.dto.PageSearchResult;
import vip.mate.wiki.dto.RelatedPageResult;
import vip.mate.wiki.job.model.WikiProcessingJobEntity;
import vip.mate.wiki.model.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 业务知识库代理控制器
 * <p>
 * 代理 mateclaw-server 的 Wiki 知识库接口，供"智能问数"配置中心使用。
 * 所有 Wiki 能力通过 {@link DataAgentWikiService} 调用，
 * 不直接依赖 mateclaw-server 内部服务实现。
 */
@Slf4j
@RestController
@RequestMapping("/v1/knowledge")
@RequiredArgsConstructor
@Tag(name = "业务知识库", description = "业务知识库管理接口（代理 mateclaw-server Wiki）")
public class DataAgentWikiController {

    private final DataAgentWikiService wikiService;

    // ==================== Knowledge Base ====================

    /**
     * 获取知识库列表
     */
    @GetMapping("/knowledge-bases")
    @Operation(summary = "知识库列表", description = "获取当前工作区下的所有知识库")
    public R<List<WikiKnowledgeBaseEntity>> listKBs(
            @RequestParam(value = "workspaceId", defaultValue = "1") Long workspaceId) {
        return R.ok(wikiService.listKBs(workspaceId));
    }

    /**
     * 按 Agent 获取知识库
     */
    @GetMapping("/knowledge-bases/agent/{agentId}")
    @Operation(summary = "按 Agent 获取知识库", description = "获取指定 Agent 关联的知识库列表")
    public R<List<WikiKnowledgeBaseEntity>> listKBsByAgent(
            @PathVariable Long agentId,
            @RequestParam(value = "workspaceId", defaultValue = "1") Long workspaceId) {
        return R.ok(wikiService.listKBsByAgent(agentId, workspaceId));
    }

    /**
     * 获取知识库详情
     */
    @GetMapping("/knowledge-bases/{id}")
    @Operation(summary = "知识库详情", description = "根据 ID 获取知识库详细信息")
    public R<WikiKnowledgeBaseEntity> getKB(@PathVariable Long id) {
        WikiKnowledgeBaseEntity kb = wikiService.getKB(id);
        if (kb == null) {
            return R.fail(404, "知识库不存在");
        }
        return R.ok(kb);
    }

    /**
     * 创建知识库
     */
    @PostMapping("/knowledge-bases")
    @Operation(summary = "创建知识库", description = "新建一个业务知识库")
    public R<WikiKnowledgeBaseEntity> createKB(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String description = (String) body.get("description");
        Long agentId = body.get("agentId") != null ? Long.valueOf(body.get("agentId").toString()) : null;
        Long workspaceId = body.get("workspaceId") != null ? Long.valueOf(body.get("workspaceId").toString()) : 1L;
        return R.ok(wikiService.createKB(name, description, agentId, workspaceId));
    }

    /**
     * 更新知识库
     */
    @PutMapping("/knowledge-bases/{id}")
    @Operation(summary = "更新知识库", description = "更新知识库的名称、描述或绑定模型")
    public R<WikiKnowledgeBaseEntity> updateKB(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(wikiService.updateKB(id, body));
    }

    /**
     * 删除知识库（级联删除所有关联数据）
     */
    @DeleteMapping("/knowledge-bases/{id}")
    @Operation(summary = "删除知识库", description = "级联删除知识库及其所有原始材料、页面、切片等")
    public R<Map<String, Object>> deleteKB(@PathVariable Long id) {
        return R.ok(wikiService.deleteKB(id));
    }

    /**
     * 获取知识库配置
     */
    @GetMapping("/knowledge-bases/{id}/config")
    @Operation(summary = "获取知识库配置", description = "获取知识库的 Wiki 处理规则配置")
    public R<Map<String, String>> getConfig(@PathVariable Long id) {
        String content = wikiService.getKBConfig(id);
        if (content == null) {
            return R.fail(404, "知识库不存在");
        }
        return R.ok(Map.of("content", content));
    }

    /**
     * 更新知识库配置
     */
    @PutMapping("/knowledge-bases/{id}/config")
    @Operation(summary = "更新知识库配置", description = "更新知识库的 Wiki 处理规则配置")
    public R<Void> updateConfig(@PathVariable Long id, @RequestBody Map<String, String> body) {
        wikiService.updateKBConfig(id, body.get("content"));
        return R.ok();
    }

    // ==================== Directory Scan ====================

    /**
     * 设置知识库关联目录
     */
    @PutMapping("/knowledge-bases/{id}/source-directory")
    @Operation(summary = "设置关联目录", description = "设置知识库的本地目录扫描路径")
    public R<Void> setSourceDirectory(@PathVariable Long id, @RequestBody Map<String, String> body) {
        wikiService.setSourceDirectory(id, body.get("path"));
        return R.ok();
    }

    /**
     * 扫描关联目录导入文件
     */
    @PostMapping("/knowledge-bases/{id}/scan")
    @Operation(summary = "扫描目录", description = "扫描知识库关联目录并导入文件")
    public R<Map<String, Object>> scanDirectory(@PathVariable Long id) {
        return R.ok(wikiService.scanDirectory(id));
    }

    // ==================== Raw Materials ====================

    /**
     * 获取原始材料列表（含每条材料生成的页面数）
     */
    @GetMapping("/knowledge-bases/{kbId}/raw")
    @Operation(summary = "原始材料列表", description = "获取指定知识库下的所有原始材料，含页面数")
    public R<List<Map<String, Object>>> listRaw(@PathVariable Long kbId) {
        return R.ok(wikiService.listRawMaterials(kbId));
    }

    /**
     * 添加文本材料
     */
    @PostMapping("/knowledge-bases/{kbId}/raw/text")
    @Operation(summary = "添加文本材料", description = "向知识库添加纯文本形式的原始材料")
    public R<WikiRawMaterialEntity> addRawText(@PathVariable Long kbId, @RequestBody Map<String, String> body) {
        return R.ok(wikiService.addRawText(kbId, body.get("title"), body.get("content")));
    }

    /**
     * 上传文件材料
     */
    @PostMapping(value = "/knowledge-bases/{kbId}/raw/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传文件材料", description = "向知识库上传文件作为原始材料（支持 pdf/docx/xlsx/pptx/image/text）")
    public R<WikiRawMaterialEntity> uploadRaw(@PathVariable Long kbId, @RequestParam("file") MultipartFile file)
            throws IOException {
        return R.ok(wikiService.uploadRaw(kbId, file));
    }

    /**
     * 删除原始材料
     */
    @DeleteMapping("/knowledge-bases/{kbId}/raw/{rawId}")
    @Operation(summary = "删除原始材料", description = "删除指定的原始材料及其关联数据")
    public R<Void> deleteRaw(@PathVariable Long kbId, @PathVariable Long rawId) {
        wikiService.deleteRaw(kbId, rawId);
        return R.ok();
    }

    /**
     * 重新处理材料
     */
    @PostMapping("/knowledge-bases/{kbId}/raw/{rawId}/reprocess")
    @Operation(summary = "重新处理材料", description = "重新触发指定材料的处理流程")
    public R<Void> reprocessRaw(@PathVariable Long kbId, @PathVariable Long rawId,
                                 @RequestParam(defaultValue = "false") boolean force) {
        wikiService.reprocessRaw(kbId, rawId, force);
        return R.ok();
    }

    /**
     * 取消材料处理
     */
    @PostMapping("/knowledge-bases/{kbId}/raw/{rawId}/cancel")
    @Operation(summary = "取消处理", description = "取消正在进行的材料处理")
    public R<Void> cancelRaw(@PathVariable Long kbId, @PathVariable Long rawId) {
        wikiService.cancelRaw(kbId, rawId);
        return R.ok();
    }

    /**
     * 下载原始材料
     */
    @GetMapping("/knowledge-bases/{kbId}/raw/{rawId}/download")
    @Operation(summary = "下载原始材料", description = "下载原始材料的原始文件或文本内容")
    public ResponseEntity<org.springframework.core.io.Resource> downloadRaw(
            @PathVariable Long kbId, @PathVariable Long rawId) throws IOException {
        return wikiService.downloadRaw(kbId, rawId);
    }

    // ==================== Processing ====================

    /**
     * 触发知识库处理
     */
    @PostMapping("/knowledge-bases/{kbId}/process")
    @Operation(summary = "触发处理", description = "触发知识库的全量处理流程")
    public R<Map<String, Object>> processKB(@PathVariable Long kbId,
                                             @RequestParam(defaultValue = "false") boolean force) {
        return R.ok(wikiService.processKB(kbId, force));
    }

    /**
     * 获取处理状态
     */
    @GetMapping("/knowledge-bases/{kbId}/processing-status")
    @Operation(summary = "处理状态", description = "获取知识库的当前处理状态和进度")
    public R<Map<String, Object>> getProcessingStatus(@PathVariable Long kbId) {
        return R.ok(wikiService.getProcessingStatus(kbId));
    }

    /**
     * 订阅处理进度 SSE
     */
    @GetMapping(value = "/knowledge-bases/{kbId}/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE 进度推送", description = "订阅知识库处理进度的 Server-Sent Events")
    public SseEmitter subscribeProgress(@PathVariable Long kbId) {
        return wikiService.subscribeProgress(kbId);
    }

    // ==================== Wiki Pages ====================

    /**
     * 获取页面列表
     */
    @GetMapping("/knowledge-bases/{kbId}/pages")
    @Operation(summary = "页面列表", description = "获取知识库下的所有 Wiki 页面，可按原始材料过滤")
    public R<List<WikiPageEntity>> listPages(@PathVariable Long kbId,
                                              @RequestParam(required = false) Long rawId) {
        return R.ok(wikiService.listPages(kbId, rawId));
    }

    /**
     * 获取页面详情
     */
    @GetMapping("/knowledge-bases/{kbId}/pages/{slug}")
    @Operation(summary = "页面详情", description = "根据 slug 获取 Wiki 页面的完整内容")
    public R<WikiPageEntity> getPage(@PathVariable Long kbId, @PathVariable String slug) {
        WikiPageEntity page = wikiService.getPage(kbId, slug);
        if (page == null) {
            return R.fail(404, "页面不存在");
        }
        return R.ok(page);
    }

    /**
     * 更新页面内容
     */
    @PutMapping("/knowledge-bases/{kbId}/pages/{slug}")
    @Operation(summary = "更新页面", description = "手动编辑 Wiki 页面内容")
    public R<WikiPageEntity> updatePage(@PathVariable Long kbId, @PathVariable String slug,
                                         @RequestBody Map<String, String> body) {
        return R.ok(wikiService.updatePage(kbId, slug, body.get("content"), body.get("summary")));
    }

    /**
     * 删除页面
     */
    @DeleteMapping("/knowledge-bases/{kbId}/pages/{slug}")
    @Operation(summary = "删除页面", description = "删除指定的 Wiki 页面")
    public R<Void> deletePage(@PathVariable Long kbId, @PathVariable String slug) {
        wikiService.deletePage(kbId, slug);
        return R.ok();
    }

    /**
     * 批量删除页面
     */
    @DeleteMapping("/knowledge-bases/{kbId}/pages/batch")
    @Operation(summary = "批量删除页面", description = "批量删除指定的 Wiki 页面")
    public R<Integer> batchDeletePages(@PathVariable Long kbId, @RequestBody List<String> slugs) {
        return R.ok(wikiService.batchDeletePages(kbId, slugs));
    }

    /**
     * 获取反向链接
     */
    @GetMapping("/knowledge-bases/{kbId}/pages/{slug}/backlinks")
    @Operation(summary = "反向链接", description = "获取指向该页面的其他页面")
    public R<List<WikiPageEntity>> getBacklinks(@PathVariable Long kbId, @PathVariable String slug) {
        return R.ok(wikiService.getBacklinks(kbId, slug));
    }

    /**
     * 列出归档页面
     */
    @GetMapping("/knowledge-bases/{kbId}/pages/archived")
    @Operation(summary = "归档页面", description = "列出知识库中所有已归档的页面")
    public R<List<WikiPageEntity>> listArchivedPages(@PathVariable Long kbId) {
        return R.ok(wikiService.listArchivedPages(kbId));
    }

    /**
     * 归档页面
     */
    @PostMapping("/knowledge-bases/{kbId}/pages/{slug}/archive")
    @Operation(summary = "归档页面", description = "将指定页面归档（软删除，可恢复）")
    public R<Map<String, Object>> archivePage(@PathVariable Long kbId, @PathVariable String slug) {
        return R.ok(wikiService.archivePage(kbId, slug));
    }

    /**
     * 取消归档
     */
    @PostMapping("/knowledge-bases/{kbId}/pages/{slug}/unarchive")
    @Operation(summary = "取消归档", description = "恢复已归档的页面")
    public R<Map<String, Object>> unarchivePage(@PathVariable Long kbId, @PathVariable String slug) {
        return R.ok(wikiService.unarchivePage(kbId, slug));
    }

    // ==================== Transformations ====================

    @GetMapping("/knowledge-bases/{kbId}/transformations")
    @Operation(summary = "转换模板列表", description = "获取知识库下的所有转换模板")
    public R<List<WikiTransformationEntity>> listTransformations(
            @PathVariable Long kbId) {
        return R.ok(wikiService.listTransformations(kbId));
    }

    @PostMapping("/knowledge-bases/{kbId}/transformations")
    @Operation(summary = "创建转换模板", description = "为知识库创建新的转换模板")
    public R<WikiTransformationEntity> createTransformation(
            @PathVariable Long kbId, @RequestBody WikiTransformationEntity body) {
        return R.ok(wikiService.createTransformation(kbId, body));
    }

    @PutMapping("/transformations/{id}")
    @Operation(summary = "更新转换模板", description = "更新转换模板的配置")
    public R<WikiTransformationEntity> updateTransformation(
            @PathVariable Long id, @RequestBody WikiTransformationEntity body) {
        return R.ok(wikiService.updateTransformation(id, body));
    }

    @DeleteMapping("/transformations/{id}")
    @Operation(summary = "删除转换模板", description = "删除指定的转换模板")
    public R<Void> deleteTransformation(@PathVariable Long id) {
        wikiService.deleteTransformation(id);
        return R.ok();
    }

    @PostMapping("/transformations/{id}/apply")
    @Operation(summary = "应用转换", description = "对指定的原始材料或页面应用转换模板")
    public R<WikiTransformationRunEntity> applyTransformation(
            @PathVariable Long id, @RequestBody Map<String, Object> body,
            @RequestParam(defaultValue = "false") boolean sync) {
        WikiTransformationRunEntity run = wikiService.applyTransformation(id, body, sync);
        if (run != null) {
            return R.ok(run);
        }
        return R.ok();
    }

    @PostMapping("/transformations/{id}/aggregate")
    @Operation(summary = "聚合转换", description = "将转换模板的所有运行结果聚合为知识库级合成页面")
    public R<Map<String, Object>> aggregateTransformation(
            @PathVariable Long id, @RequestParam Long kbId) {
        try {
            return R.ok(wikiService.aggregateTransformation(id, kbId));
        } catch (IllegalStateException e) {
            return R.fail(409, e.getMessage());
        } catch (IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        }
    }

    @GetMapping("/transformations/{id}/runs")
    @Operation(summary = "转换运行记录", description = "获取转换模板的运行记录列表")
    public R<List<WikiTransformationRunEntity>> listTransformationRuns(
            @PathVariable Long id) {
        return R.ok(wikiService.listTransformationRuns(id));
    }

    @PostMapping("/transformation-runs/{runId}/cancel")
    @Operation(summary = "取消运行", description = "取消正在进行的转换运行")
    public R<Void> cancelTransformationRun(@PathVariable Long runId) {
        boolean cancelled = wikiService.cancelTransformationRun(runId);
        if (!cancelled) {
            return R.fail(409, "运行不在进行中");
        }
        return R.ok();
    }

    @PostMapping("/transformation-runs/{runId}/save-as-page")
    @Operation(summary = "保存为页面", description = "将转换运行结果保存为Wiki页面")
    public R<Map<String, Object>> saveRunAsPage(@PathVariable Long runId) {
        try {
            return R.ok(wikiService.saveRunAsPage(runId));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        }
    }

    @DeleteMapping("/transformation-runs/{runId}")
    @Operation(summary = "删除运行记录", description = "删除转换运行记录")
    public R<Void> deleteTransformationRun(@PathVariable Long runId) {
        wikiService.deleteTransformationRun(runId);
        return R.ok();
    }

    // ==================== Hot Cache ====================

    @GetMapping("/knowledge-bases/{kbId}/hot-cache")
    @Operation(summary = "热缓存快照", description = "获取知识库的近期活动快照")
    public R<WikiHotCacheEntity> getHotCache(@PathVariable Long kbId) {
        return R.ok(wikiService.getHotCache(kbId));
    }

    @PostMapping("/knowledge-bases/{kbId}/hot-cache/regenerate")
    @Operation(summary = "重新生成热缓存", description = "手动触发热缓存重建")
    public R<Void> regenerateHotCache(@PathVariable Long kbId) {
        wikiService.regenerateHotCache(kbId);
        return R.ok();
    }

    @DeleteMapping("/knowledge-bases/{kbId}/hot-cache")
    @Operation(summary = "重置热缓存", description = "清空知识库的热缓存")
    public R<Void> resetHotCache(@PathVariable Long kbId) {
        wikiService.resetHotCache(kbId);
        return R.ok();
    }

    // ==================== Relations / Stats / Jobs ====================

    @GetMapping("/knowledge-bases/{kbId}/pages/{slug}/related")
    @Operation(summary = "相关页面", description = "获取与指定页面语义相关的其他页面")
    public R<List<RelatedPageResult>> getRelatedPages(
            @PathVariable Long kbId, @PathVariable String slug,
            @RequestParam(defaultValue = "5") int topK) {
        return R.ok(wikiService.getRelatedPages(kbId, slug, topK));
    }

    @GetMapping("/knowledge-bases/{kbId}/pages/{pageId}/citations")
    @Operation(summary = "引用来源", description = "获取页面的引用来源信息")
    public R<List<PageCitationWithRaw>> getPageCitations(
            @PathVariable Long kbId, @PathVariable Long pageId) {
        return R.ok(wikiService.getPageCitations(kbId, pageId));
    }

    @GetMapping("/knowledge-bases/{kbId}/jobs")
    @Operation(summary = "处理任务", description = "获取知识库的处理任务列表")
    public R<List<WikiProcessingJobEntity>> getJobs(
            @PathVariable Long kbId, @RequestParam(required = false) Long rawId) {
        return R.ok(wikiService.getJobs(kbId, rawId));
    }

    @GetMapping("/knowledge-bases/{kbId}/stats")
    @Operation(summary = "知识库统计", description = "获取知识库的完整统计数据")
    public R<Map<String, Object>> kbStats(@PathVariable Long kbId) {
        return R.ok(wikiService.kbStats(kbId));
    }

    @PostMapping("/knowledge-bases/{kbId}/pages/{slug}/enrich")
    @Operation(summary = "富化页面", description = "触发页面的链接富化处理")
    public R<Map<String, Object>> enrichPage(@PathVariable Long kbId, @PathVariable String slug) {
        return R.ok(wikiService.enrichPage(kbId, slug));
    }

    @PostMapping("/knowledge-bases/{kbId}/pages/{slug}/repair")
    @Operation(summary = "修复页面", description = "触发页面的本地修复处理")
    public R<Map<String, Object>> repairPage(@PathVariable Long kbId, @PathVariable String slug) {
        return R.ok(wikiService.repairPage(kbId, slug));
    }

    @PostMapping("/knowledge-bases/{kbId}/search-preview")
    @Operation(summary = "搜索预览", description = "预览知识库搜索效果")
    public R<List<PageSearchResult>> searchPreview(
            @PathVariable Long kbId, @RequestBody Map<String, Object> body) {
        return R.ok(wikiService.searchPreview(kbId, body));
    }
}
