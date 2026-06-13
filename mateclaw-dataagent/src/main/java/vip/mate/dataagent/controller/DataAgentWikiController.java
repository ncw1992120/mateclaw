package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vip.mate.channel.web.Utf8SseEmitter;
import vip.mate.common.result.R;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import vip.mate.wiki.WikiProperties;
import vip.mate.wiki.dto.PageCitationWithRaw;
import vip.mate.wiki.dto.PageSearchResult;
import vip.mate.wiki.dto.RelatedPageResult;
import vip.mate.wiki.hotcache.HotCacheUpdateReason;
import vip.mate.wiki.hotcache.HotCacheUpdateScheduler;
import vip.mate.wiki.hotcache.WikiHotCacheService;
import vip.mate.wiki.job.WikiProcessingJobService;
import vip.mate.wiki.job.event.WikiJobCreatedEvent;
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

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 业务知识库代理控制器
 * <p>
 * 代理 mateclaw-server 的 Wiki 知识库接口，供"智能问数"配置中心使用。
 * 支持知识库 CRUD、原材料管理、页面查看、处理触发、SSE 进度推送等完整功能。
 * 设计严格对标 mateclaw-server 的 {@link vip.mate.wiki.controller.WikiController}。
 */
@Slf4j
@RestController
@RequestMapping("/v1/knowledge")
@RequiredArgsConstructor
@Tag(name = "业务知识库", description = "业务知识库管理接口（代理 mateclaw-server Wiki）")
public class DataAgentWikiController {

    private final WikiKnowledgeBaseService kbService;
    private final WikiRawMaterialService rawService;
    private final WikiPageService pageService;
    private final WikiProcessingService processingService;
    private final WikiDirectoryScanService scanService;
    private final WikiProperties properties;
    private final WikiProgressBus progressBus;

    private final WikiTransformationService transformationService;
    private final WikiTransformationExecutor transformationExecutor;
    private final WikiTransformationAggregator transformationAggregator;
    private final WikiHotCacheService hotCacheService;
    private final HotCacheUpdateScheduler hotCacheScheduler;
    private final WikiRelationService relationService;
    private final WikiProcessingJobService jobService;
    private final WikiProcessingJobMapper jobMapper;
    private final WikiPageCitationMapper citationMapper;
    private final HybridRetriever hybridRetriever;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final WikiEmbeddingService embeddingService;

    // ==================== Knowledge Base ====================

    /**
     * 获取知识库列表
     */
    @GetMapping("/knowledge-bases")
    @Operation(summary = "知识库列表", description = "获取当前工作区下的所有知识库")
    public R<List<WikiKnowledgeBaseEntity>> listKBs(
            @RequestParam(value = "workspaceId", defaultValue = "1") Long workspaceId) {
        return R.ok(withLivePageCount(kbService.listByWorkspace(workspaceId)));
    }

    /**
     * 按 Agent 获取知识库
     */
    @GetMapping("/knowledge-bases/agent/{agentId}")
    @Operation(summary = "按 Agent 获取知识库", description = "获取指定 Agent 关联的知识库列表")
    public R<List<WikiKnowledgeBaseEntity>> listKBsByAgent(
            @PathVariable Long agentId,
            @RequestParam(value = "workspaceId", defaultValue = "1") Long workspaceId) {
        List<WikiKnowledgeBaseEntity> kbs = kbService.listByAgentId(agentId);
        return R.ok(withLivePageCount(kbs.stream()
                .filter(kb -> kb.getWorkspaceId() == null || kb.getWorkspaceId().equals(workspaceId))
                .collect(java.util.stream.Collectors.toList())));
    }

    /**
     * 获取知识库详情
     */
    @GetMapping("/knowledge-bases/{id}")
    @Operation(summary = "知识库详情", description = "根据 ID 获取知识库详细信息")
    public R<WikiKnowledgeBaseEntity> getKB(@PathVariable Long id) {
        WikiKnowledgeBaseEntity kb = kbService.getById(id);
        if (kb == null) {
            return R.fail(404, "知识库不存在");
        }
        return R.ok(withLivePageCount(kb));
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
        return R.ok(kbService.create(name, description, agentId, workspaceId));
    }

    /**
     * 更新知识库
     */
    @PutMapping("/knowledge-bases/{id}")
    @Operation(summary = "更新知识库", description = "更新知识库的名称、描述或绑定模型")
    public R<WikiKnowledgeBaseEntity> updateKB(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String description = (String) body.get("description");
        Long agentId = body.get("agentId") != null ? Long.valueOf(body.get("agentId").toString()) : null;
        kbService.update(id, name, description, agentId);

        /* 更新绑定向量模型 */
        if (body.containsKey("embeddingModelId")) {
            Object v = body.get("embeddingModelId");
            Long embeddingModelId = null;
            if (v != null && !v.toString().isBlank()) {
                embeddingModelId = Long.valueOf(v.toString());
            }
            kbService.updateEmbeddingModelId(id, embeddingModelId);
        }
        return R.ok(kbService.getById(id));
    }

    /**
     * 删除知识库（级联删除所有关联数据）
     */
    @DeleteMapping("/knowledge-bases/{id}")
    @Operation(summary = "删除知识库", description = "级联删除知识库及其所有原始材料、页面、切片等")
    public R<Map<String, Object>> deleteKB(@PathVariable Long id) {
        WikiKnowledgeBaseService.CascadeDeleteResult result = kbService.delete(id);
        return R.ok(Map.of(
                "rawMaterialCount", result.rawMaterialCount(),
                "pageCount", result.pageCount(),
                "chunkCount", result.chunkCount(),
                "citationCount", result.citationCount(),
                "processingJobCount", result.processingJobCount(),
                "kbName", result.kbName()
        ));
    }

    /**
     * 获取知识库配置
     */
    @GetMapping("/knowledge-bases/{id}/config")
    @Operation(summary = "获取知识库配置", description = "获取知识库的 Wiki 处理规则配置")
    public R<Map<String, String>> getConfig(@PathVariable Long id) {
        WikiKnowledgeBaseEntity kb = kbService.getById(id);
        if (kb == null) {
            return R.fail(404, "知识库不存在");
        }
        return R.ok(Map.of("content", kb.getConfigContent() != null ? kb.getConfigContent() : ""));
    }

    /**
     * 更新知识库配置
     */
    @PutMapping("/knowledge-bases/{id}/config")
    @Operation(summary = "更新知识库配置", description = "更新知识库的 Wiki 处理规则配置")
    public R<Void> updateConfig(@PathVariable Long id, @RequestBody Map<String, String> body) {
        kbService.updateConfig(id, body.get("content"));
        return R.ok();
    }

    // ==================== Directory Scan ====================

    /**
     * 设置知识库关联目录
     */
    @PutMapping("/knowledge-bases/{id}/source-directory")
    @Operation(summary = "设置关联目录", description = "设置知识库的本地目录扫描路径")
    public R<Void> setSourceDirectory(@PathVariable Long id, @RequestBody Map<String, String> body) {
        kbService.updateSourceDirectory(id, body.get("path"));
        return R.ok();
    }

    /**
     * 扫描关联目录导入文件
     */
    @PostMapping("/knowledge-bases/{id}/scan")
    @Operation(summary = "扫描目录", description = "扫描知识库关联目录并导入文件")
    public R<Map<String, Object>> scanDirectory(@PathVariable Long id) {
        WikiDirectoryScanService.ScanResult result = scanService.scan(id);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scanned", result.scanned());
        response.put("added", result.added());
        response.put("skipped", result.skipped());
        response.put("errors", result.errors());
        return R.ok(response);
    }

    // ==================== Raw Materials ====================

    /**
     * 获取原始材料列表（含每条材料生成的页面数）
     */
    @GetMapping("/knowledge-bases/{kbId}/raw")
    @Operation(summary = "原始材料列表", description = "获取指定知识库下的所有原始材料，含页面数")
    public R<List<Map<String, Object>>> listRaw(@PathVariable Long kbId) {
        List<WikiRawMaterialEntity> raws = rawService.listByKbId(kbId);
        List<Map<String, Object>> result = new java.util.ArrayList<>(raws.size());
        for (WikiRawMaterialEntity raw : raws) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", raw.getId());
            item.put("kbId", raw.getKbId());
            item.put("title", raw.getTitle());
            item.put("sourceType", raw.getSourceType());
            item.put("processingStatus", raw.getProcessingStatus());
            item.put("errorMessage", raw.getErrorMessage());
            item.put("progressPhase", raw.getProgressPhase());
            item.put("progressDone", raw.getProgressDone());
            item.put("progressTotal", raw.getProgressTotal());
            item.put("contentHash", raw.getContentHash());
            item.put("createTime", raw.getCreateTime());
            item.put("updateTime", raw.getUpdateTime());
            item.put("pageCount", pageService.countBySourceRawId(kbId, raw.getId()));
            result.add(item);
        }
        return R.ok(result);
    }

    /**
     * 添加文本材料
     */
    @PostMapping("/knowledge-bases/{kbId}/raw/text")
    @Operation(summary = "添加文本材料", description = "向知识库添加纯文本形式的原始材料")
    public R<WikiRawMaterialEntity> addRawText(@PathVariable Long kbId, @RequestBody Map<String, String> body) {
        return R.ok(rawService.addText(kbId, body.get("title"), body.get("content")));
    }

    /**
     * 上传文件材料
     * <p>
     * 严格对标原始 WikiController：文本文件直接存数据库，二进制文件存磁盘。
     */
    @PostMapping(value = "/knowledge-bases/{kbId}/raw/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传文件材料", description = "向知识库上传文件作为原始材料（支持 pdf/docx/xlsx/pptx/image/text）")
    public R<WikiRawMaterialEntity> uploadRaw(@PathVariable Long kbId, @RequestParam("file") MultipartFile file)
            throws IOException {
        String originalName = file.getOriginalFilename();
        String extension = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf(".") + 1).toLowerCase()
                : "txt";

        String sourceType = switch (extension) {
            case "pdf" -> "pdf";
            case "docx", "doc" -> "docx";
            case "xlsx", "xls" -> "xlsx";
            case "pptx", "ppt" -> "pptx";
            case "html", "htm" -> "html";
            case "txt", "md", "csv" -> "text";
            case "png", "jpg", "jpeg", "webp", "gif", "bmp", "tiff", "tif" -> "image";
            default -> "text";
        };

        if ("text".equals(sourceType)) {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            return R.ok(rawService.addText(kbId, originalName, content));
        } else {
            Path uploadDir = Paths.get(properties.getUploadDir()).toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);
            Path targetPath = uploadDir.resolve(System.currentTimeMillis() + "_" + originalName);
            file.transferTo(targetPath);
            return R.ok(rawService.addFile(kbId, originalName, sourceType,
                    file.getContentType(), targetPath.toString(), file.getSize()));
        }
    }

    /**
     * 删除原始材料
     */
    @DeleteMapping("/knowledge-bases/{kbId}/raw/{rawId}")
    @Operation(summary = "删除原始材料", description = "删除指定的原始材料及其关联数据")
    public R<Void> deleteRaw(@PathVariable Long kbId, @PathVariable Long rawId) {
        WikiRawMaterialEntity raw = rawService.getById(rawId);
        if (raw == null || !kbId.equals(raw.getKbId())) {
            return R.fail(404, "原始材料不存在于该知识库");
        }
        rawService.delete(rawId);
        kbService.decrementRawCount(kbId);
        return R.ok();
    }

    /**
     * 重新处理材料
     */
    @PostMapping("/knowledge-bases/{kbId}/raw/{rawId}/reprocess")
    @Operation(summary = "重新处理材料", description = "重新触发指定材料的处理流程")
    public R<Void> reprocessRaw(@PathVariable Long kbId, @PathVariable Long rawId,
                                 @RequestParam(defaultValue = "false") boolean force) {
        WikiRawMaterialEntity raw = rawService.getById(rawId);
        if (raw == null || !kbId.equals(raw.getKbId())) {
            return R.fail(404, "原始材料不存在于该知识库");
        }
        if (force) {
            rawService.setLastProcessedHash(rawId, null);
        }
        rawService.reprocess(rawId);
        return R.ok();
    }

    /**
     * 取消材料处理
     */
    @PostMapping("/knowledge-bases/{kbId}/raw/{rawId}/cancel")
    @Operation(summary = "取消处理", description = "取消正在进行的材料处理")
    public R<Void> cancelRaw(@PathVariable Long kbId, @PathVariable Long rawId) {
        WikiRawMaterialEntity raw = rawService.getById(rawId);
        if (raw == null || !kbId.equals(raw.getKbId())) {
            return R.fail(404, "原始材料不存在于该知识库");
        }
        rawService.requestCancel(rawId);
        return R.ok();
    }

    /**
     * 下载原始材料
     */
    @GetMapping("/knowledge-bases/{kbId}/raw/{rawId}/download")
    @Operation(summary = "下载原始材料", description = "下载原始材料的原始文件或文本内容")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> downloadRaw(
            @PathVariable Long kbId, @PathVariable Long rawId) throws IOException {
        WikiRawMaterialEntity raw = rawService.getById(rawId);
        if (raw == null || !kbId.equals(raw.getKbId())) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }

        String rawTitle = raw.getTitle();
        String filename = (rawTitle != null && !rawTitle.isBlank()) ? rawTitle : ("source-" + rawId);

        org.springframework.core.io.Resource resource;
        long contentLength;
        org.springframework.http.MediaType mediaType;
        String sourceType = raw.getSourceType();

        if ("text".equals(sourceType)) {
            String content = raw.getOriginalContent();
            if (content == null) {
                return org.springframework.http.ResponseEntity.notFound().build();
            }
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            resource = new org.springframework.core.io.ByteArrayResource(bytes);
            contentLength = bytes.length;
            mediaType = org.springframework.http.MediaType.parseMediaType("text/plain;charset=UTF-8");
            if (!filename.contains(".")) filename = filename + ".txt";
        } else {
            String sourcePath = raw.getSourcePath();
            if (sourcePath == null || sourcePath.isBlank()) {
                return org.springframework.http.ResponseEntity.notFound().build();
            }
            Path path = Paths.get(sourcePath).toAbsolutePath().normalize();
            Path uploadDir = Paths.get(properties.getUploadDir()).toAbsolutePath().normalize();
            if (!path.startsWith(uploadDir)) {
                log.warn("[Wiki] Download rejected: rawId={} path={} outside uploadDir={}", rawId, path, uploadDir);
                return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
            }
            if (!Files.isRegularFile(path)) {
                return org.springframework.http.ResponseEntity.notFound().build();
            }
            resource = new org.springframework.core.io.FileSystemResource(path);
            contentLength = Files.size(path);
            mediaType = org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;
        }

        String asciiFallback = filename.replaceAll("[^\\x20-\\x7E]", "_")
                .replace("\"", "_").replace("\\", "_");
        String encoded = java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        String contentDisposition = "attachment; filename=\"" + asciiFallback
                + "\"; filename*=UTF-8''" + encoded;

        return org.springframework.http.ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(contentLength)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(resource);
    }

    // ==================== Processing ====================

    /**
     * 触发知识库处理
     */
    @PostMapping("/knowledge-bases/{kbId}/process")
    @Operation(summary = "触发处理", description = "触发知识库的全量处理流程")
    public R<Map<String, Object>> processKB(@PathVariable Long kbId,
                                             @RequestParam(defaultValue = "false") boolean force) {
        int queued = processingService.processKB(kbId, force);
        return R.ok(Map.of("queued", queued, "force", force));
    }

    /**
     * 获取处理状态
     */
    @GetMapping("/knowledge-bases/{kbId}/processing-status")
    @Operation(summary = "处理状态", description = "获取知识库的当前处理状态和进度")
    public R<Map<String, Object>> getProcessingStatus(@PathVariable Long kbId) {
        WikiKnowledgeBaseEntity kb = kbService.getById(kbId);
        if (kb == null) {
            return R.fail(404, "知识库不存在");
        }

        List<WikiRawMaterialEntity> rawList = rawService.listByKbId(kbId);
        long pending = rawList.stream().filter(r -> "pending".equals(r.getProcessingStatus())).count();
        long processing = rawList.stream().filter(r -> "processing".equals(r.getProcessingStatus())).count();
        long completed = rawList.stream().filter(r -> "completed".equals(r.getProcessingStatus())).count();
        long failed = rawList.stream().filter(r -> "failed".equals(r.getProcessingStatus())).count();

        return R.ok(Map.of(
                "status", kb.getStatus(),
                "pending", pending,
                "processing", processing,
                "completed", completed,
                "failed", failed,
                "totalRaw", rawList.size(),
                "totalPages", kb.getPageCount()
        ));
    }

    /**
     * 订阅处理进度 SSE
     */
    @GetMapping(value = "/knowledge-bases/{kbId}/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE 进度推送", description = "订阅知识库处理进度的 Server-Sent Events")
    public SseEmitter subscribeProgress(@PathVariable Long kbId) {
        SseEmitter emitter = new Utf8SseEmitter(30L * 60 * 1000);
        progressBus.subscribe(kbId, emitter);

        emitter.onCompletion(() -> {
            progressBus.unsubscribe(kbId, emitter);
            log.debug("[Wiki SSE] emitter completed: kbId={}", kbId);
        });
        emitter.onTimeout(() -> {
            progressBus.unsubscribe(kbId, emitter);
            try { emitter.complete(); } catch (Exception ignore) { }
            log.debug("[Wiki SSE] emitter timeout: kbId={}", kbId);
        });
        emitter.onError(e -> {
            progressBus.unsubscribe(kbId, emitter);
            log.debug("[Wiki SSE] emitter error: kbId={}, cause={}", kbId, e.getMessage());
        });

        try {
            emitter.send(SseEmitter.event().name(WikiProgressBus.EVENT_HEARTBEAT)
                    .data("{\"ts\":" + System.currentTimeMillis() + ",\"hello\":true}"));
        } catch (Exception e) {
            log.debug("[Wiki SSE] initial heartbeat send failed: {}", e.getMessage());
        }

        return emitter;
    }

    // ==================== Wiki Pages ====================

    /**
     * 获取页面列表
     */
    @GetMapping("/knowledge-bases/{kbId}/pages")
    @Operation(summary = "页面列表", description = "获取知识库下的所有 Wiki 页面，可按原始材料过滤")
    public R<List<WikiPageEntity>> listPages(@PathVariable Long kbId,
                                              @RequestParam(required = false) Long rawId) {
        if (rawId != null) {
            return R.ok(pageService.listBySourceRawId(kbId, rawId));
        }
        return R.ok(pageService.listByKbId(kbId));
    }

    /**
     * 获取页面详情
     */
    @GetMapping("/knowledge-bases/{kbId}/pages/{slug}")
    @Operation(summary = "页面详情", description = "根据 slug 获取 Wiki 页面的完整内容")
    public R<WikiPageEntity> getPage(@PathVariable Long kbId, @PathVariable String slug) {
        WikiPageEntity page = pageService.getBySlug(kbId, slug);
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
        return R.ok(pageService.updatePageManually(kbId, slug, body.get("content"), body.get("summary")));
    }

    /**
     * 删除页面
     */
    @DeleteMapping("/knowledge-bases/{kbId}/pages/{slug}")
    @Operation(summary = "删除页面", description = "删除指定的 Wiki 页面")
    public R<Void> deletePage(@PathVariable Long kbId, @PathVariable String slug) {
        pageService.delete(kbId, slug);
        kbService.setPageCount(kbId, pageService.countByKbId(kbId));
        return R.ok();
    }

    /**
     * 批量删除页面
     */
    @DeleteMapping("/knowledge-bases/{kbId}/pages/batch")
    @Operation(summary = "批量删除页面", description = "批量删除指定的 Wiki 页面")
    public R<Integer> batchDeletePages(@PathVariable Long kbId, @RequestBody List<String> slugs) {
        int deleted = pageService.batchDelete(kbId, slugs);
        kbService.setPageCount(kbId, pageService.countByKbId(kbId));
        return R.ok(deleted);
    }

    /**
     * 获取反向链接
     */
    @GetMapping("/knowledge-bases/{kbId}/pages/{slug}/backlinks")
    @Operation(summary = "反向链接", description = "获取指向该页面的其他页面")
    public R<List<WikiPageEntity>> getBacklinks(@PathVariable Long kbId, @PathVariable String slug) {
        return R.ok(pageService.getBacklinks(kbId, slug));
    }

    /**
     * 列出归档页面
     */
    @GetMapping("/knowledge-bases/{kbId}/pages/archived")
    @Operation(summary = "归档页面", description = "列出知识库中所有已归档的页面")
    public R<List<WikiPageEntity>> listArchivedPages(@PathVariable Long kbId) {
        return R.ok(pageService.listArchivedByKbId(kbId));
    }

    /**
     * 归档页面
     */
    @PostMapping("/knowledge-bases/{kbId}/pages/{slug}/archive")
    @Operation(summary = "归档页面", description = "将指定页面归档（软删除，可恢复）")
    public R<Map<String, Object>> archivePage(@PathVariable Long kbId, @PathVariable String slug) {
        boolean changed = pageService.setArchived(kbId, slug, true);
        return R.ok(Map.of("slug", slug, "archived", true, "changed", changed));
    }

    /**
     * 取消归档
     */
    @PostMapping("/knowledge-bases/{kbId}/pages/{slug}/unarchive")
    @Operation(summary = "取消归档", description = "恢复已归档的页面")
    public R<Map<String, Object>> unarchivePage(@PathVariable Long kbId, @PathVariable String slug) {
        boolean changed = pageService.setArchived(kbId, slug, false);
        return R.ok(Map.of("slug", slug, "archived", false, "changed", changed));
    }

    // ==================== Transformations ====================

    @GetMapping("/knowledge-bases/{kbId}/transformations")
    @Operation(summary = "转换模板列表", description = "获取知识库下的所有转换模板")
    public R<List<WikiTransformationEntity>> listTransformations(
            @PathVariable Long kbId) {
        return R.ok(transformationService.listForKb(kbId, 1L));
    }

    @PostMapping("/knowledge-bases/{kbId}/transformations")
    @Operation(summary = "创建转换模板", description = "为知识库创建新的转换模板")
    public R<WikiTransformationEntity> createTransformation(
            @PathVariable Long kbId, @RequestBody WikiTransformationEntity body) {
        body.setKbId(kbId);
        body.setWorkspaceId(1L);
        return R.ok(transformationService.create(body));
    }

    @PutMapping("/transformations/{id}")
    @Operation(summary = "更新转换模板", description = "更新转换模板的配置")
    public R<WikiTransformationEntity> updateTransformation(
            @PathVariable Long id, @RequestBody WikiTransformationEntity body) {
        return R.ok(transformationService.update(id, body));
    }

    @DeleteMapping("/transformations/{id}")
    @Operation(summary = "删除转换模板", description = "删除指定的转换模板")
    public R<Void> deleteTransformation(@PathVariable Long id) {
        transformationService.delete(id);
        return R.ok();
    }

    @PostMapping("/transformations/{id}/apply")
    @Operation(summary = "应用转换", description = "对指定的原始材料或页面应用转换模板")
    public R<WikiTransformationRunEntity> applyTransformation(
            @PathVariable Long id, @RequestBody Map<String, Object> body,
            @RequestParam(defaultValue = "false") boolean sync) {
        WikiTransformationEntity t = transformationService.getById(id);
        if (t == null) return R.fail(404, "转换模板不存在");

        Object rawIdRaw = body == null ? null : body.get("rawId");
        Object pageIdRaw = body == null ? null : body.get("pageId");
        if (rawIdRaw == null && pageIdRaw == null) {
            return R.fail(400, "rawId 或 pageId 必须提供一个");
        }
        if (rawIdRaw != null && pageIdRaw != null) {
            return R.fail(400, "只能提供 rawId 或 pageId 中的一个");
        }

        if (rawIdRaw != null) {
            Long rawId = Long.valueOf(rawIdRaw.toString());
            if (sync) return R.ok(transformationExecutor.runOnRawSync(t, rawId, "manual"));
            transformationExecutor.runOnRawAsync(t, rawId, "manual");
        } else {
            Long pageId = Long.valueOf(pageIdRaw.toString());
            if (sync) return R.ok(transformationExecutor.runOnPageSync(t, pageId, "manual"));
            transformationExecutor.runOnPageAsync(t, pageId, "manual");
        }
        return R.ok();
    }

    @PostMapping("/transformations/{id}/aggregate")
    @Operation(summary = "聚合转换", description = "将转换模板的所有运行结果聚合为知识库级合成页面")
    public R<Map<String, Object>> aggregateTransformation(
            @PathVariable Long id, @RequestParam Long kbId) {
        WikiTransformationEntity t = transformationService.getById(id);
        if (t == null) return R.fail(404, "转换模板不存在");
        try {
            var res = transformationAggregator.aggregate(t, kbId, "manual");
            if (res.pageId() == null) {
                return R.fail(409, res.title());
            }
            return R.ok(Map.of(
                    "pageId", res.pageId(),
                    "slug", res.slug(),
                    "title", res.title(),
                    "sourcesUsed", res.sourcesUsed(),
                    "charsFed", res.charsFed(),
                    "created", res.created()));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        }
    }

    @GetMapping("/transformations/{id}/runs")
    @Operation(summary = "转换运行记录", description = "获取转换模板的运行记录列表")
    public R<List<WikiTransformationRunEntity>> listTransformationRuns(
            @PathVariable Long id) {
        return R.ok(transformationService.listRunsByTransformation(id, 50));
    }

    @PostMapping("/transformation-runs/{runId}/cancel")
    @Operation(summary = "取消运行", description = "取消正在进行的转换运行")
    public R<Void> cancelTransformationRun(@PathVariable Long runId) {
        boolean cancelled = transformationExecutor.cancelRun(runId);
        if (!cancelled) return R.fail(409, "运行不在进行中");
        return R.ok();
    }

    @PostMapping("/transformation-runs/{runId}/save-as-page")
    @Operation(summary = "保存为页面", description = "将转换运行结果保存为Wiki页面")
    public R<Map<String, Object>> saveRunAsPage(@PathVariable Long runId) {
        try {
            var page = transformationExecutor.manualSaveRunAsPage(runId);
            if (page == null) return R.fail(503, "页面服务不可用");
            return R.ok(Map.of("pageId", page.getId(), "slug", page.getSlug(), "title", page.getTitle()));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        }
    }

    @DeleteMapping("/transformation-runs/{runId}")
    @Operation(summary = "删除运行记录", description = "删除转换运行记录")
    public R<Void> deleteTransformationRun(@PathVariable Long runId) {
        transformationService.deleteRun(runId);
        return R.ok();
    }

    // ==================== Hot Cache ====================

    @GetMapping("/knowledge-bases/{kbId}/hot-cache")
    @Operation(summary = "热缓存快照", description = "获取知识库的近期活动快照")
    public R<WikiHotCacheEntity> getHotCache(@PathVariable Long kbId) {
        return R.ok(hotCacheService.findByKb(kbId).orElse(null));
    }

    @PostMapping("/knowledge-bases/{kbId}/hot-cache/regenerate")
    @Operation(summary = "重新生成热缓存", description = "手动触发热缓存重建")
    public R<Void> regenerateHotCache(@PathVariable Long kbId) {
        hotCacheScheduler.scheduleRebuild(kbId, HotCacheUpdateReason.MANUAL);
        return R.ok();
    }

    @DeleteMapping("/knowledge-bases/{kbId}/hot-cache")
    @Operation(summary = "重置热缓存", description = "清空知识库的热缓存")
    public R<Void> resetHotCache(@PathVariable Long kbId) {
        hotCacheService.findByKb(kbId).ifPresent(row -> hotCacheService.softDelete(row.getId()));
        return R.ok();
    }

    // ==================== Relations / Stats / Jobs ====================

    @GetMapping("/knowledge-bases/{kbId}/pages/{slug}/related")
    @Operation(summary = "相关页面", description = "获取与指定页面语义相关的其他页面")
    public R<List<RelatedPageResult>> getRelatedPages(
            @PathVariable Long kbId, @PathVariable String slug,
            @RequestParam(defaultValue = "5") int topK) {
        return R.ok(relationService.relatedPages(kbId, slug, Math.min(topK, 20)));
    }

    @GetMapping("/knowledge-bases/{kbId}/pages/{pageId}/citations")
    @Operation(summary = "引用来源", description = "获取页面的引用来源信息")
    public R<List<PageCitationWithRaw>> getPageCitations(
            @PathVariable Long kbId, @PathVariable Long pageId) {
        return R.ok(citationMapper.listWithRawByPageId(pageId));
    }

    @GetMapping("/knowledge-bases/{kbId}/jobs")
    @Operation(summary = "处理任务", description = "获取知识库的处理任务列表")
    public R<List<WikiProcessingJobEntity>> getJobs(
            @PathVariable Long kbId, @RequestParam(required = false) Long rawId) {
        if (rawId != null) {
            return R.ok(jobMapper.findLatestByRawId(rawId).map(List::of).orElse(List.of()));
        }
        return R.ok(jobMapper.listQueued(kbId, 20));
    }

    @GetMapping("/knowledge-bases/{kbId}/stats")
    @Operation(summary = "知识库统计", description = "获取知识库的完整统计数据")
    public R<Map<String, Object>> kbStats(@PathVariable Long kbId) {
        int pageCount = pageService.countByKbId(kbId);
        long enrichedCount = pageService.listByKbIdWithContent(kbId).stream()
                .filter(p -> p.getContent() != null && p.getContent().contains("[["))
                .count();
        var allJobs = jobMapper.listByKbId(kbId, 200);
        Map<Long, WikiProcessingJobEntity> latestByRaw = new HashMap<>();
        for (WikiProcessingJobEntity job : allJobs) {
            latestByRaw.merge(job.getRawId(), job,
                    (a, b) -> a.getId() >= b.getId() ? a : b);
        }
        int failedJobCount = (int) latestByRaw.values().stream()
                .filter(j -> "failed".equals(j.getStatus())).count();
        int runningJobCount = (int) latestByRaw.values().stream()
                .filter(j -> "running".equals(j.getStatus())).count();
        var drift = embeddingService.describeDrift(kbId);
        return R.ok(Map.of(
                "pageCount", pageCount,
                "enrichedPageCount", enrichedCount,
                "failedJobCount", failedJobCount,
                "runningJobCount", runningJobCount,
                "embeddingDrift", drift));
    }

    @PostMapping("/knowledge-bases/{kbId}/pages/{slug}/enrich")
    @Operation(summary = "富化页面", description = "触发页面的链接富化处理")
    public R<Map<String, Object>> enrichPage(@PathVariable Long kbId, @PathVariable String slug) {
        WikiPageEntity page = pageService.getBySlug(kbId, slug);
        if (page == null) return R.fail(404, "页面不存在: " + slug);
        Long rawId = 0L;
        try {
            List<Long> rawIds = objectMapper.readValue(
                    page.getSourceRawIds() != null ? page.getSourceRawIds() : "[]",
                    new TypeReference<List<Long>>() {});
            if (!rawIds.isEmpty()) rawId = rawIds.get(0);
        } catch (Exception ignored) {}
        var job = jobService.createLightEnrich(kbId, rawId);
        eventPublisher.publishEvent(new WikiJobCreatedEvent(job.getId()));
        return R.ok(Map.of("jobId", job.getId()));
    }

    @PostMapping("/knowledge-bases/{kbId}/pages/{slug}/repair")
    @Operation(summary = "修复页面", description = "触发页面的本地修复处理")
    public R<Map<String, Object>> repairPage(@PathVariable Long kbId, @PathVariable String slug) {
        WikiPageEntity page = pageService.getBySlug(kbId, slug);
        if (page == null) return R.fail(404, "页面不存在: " + slug);
        Long rawId = 0L;
        try {
            List<Long> rawIds = objectMapper.readValue(
                    page.getSourceRawIds() != null ? page.getSourceRawIds() : "[]",
                    new TypeReference<List<Long>>() {});
            if (!rawIds.isEmpty()) rawId = rawIds.get(0);
        } catch (Exception ignored) {}
        var job = jobService.createLocalRepair(kbId, rawId, page.getId());
        eventPublisher.publishEvent(new WikiJobCreatedEvent(job.getId()));
        return R.ok(Map.of("jobId", job.getId()));
    }

    @PostMapping("/knowledge-bases/{kbId}/search-preview")
    @Operation(summary = "搜索预览", description = "预览知识库搜索效果")
    public R<List<PageSearchResult>> searchPreview(
            @PathVariable Long kbId, @RequestBody Map<String, Object> body) {
        String query = (String) body.getOrDefault("query", "");
        String mode = (String) body.getOrDefault("mode", "hybrid");
        int topK = body.containsKey("topK") ? ((Number) body.get("topK")).intValue() : 5;
        return R.ok(hybridRetriever.search(kbId, query, mode, Math.min(topK, 20)));
    }

    // ==================== Internal Helpers ====================

    /**
     * 为知识库列表补充实时页面计数
     */
    private List<WikiKnowledgeBaseEntity> withLivePageCount(List<WikiKnowledgeBaseEntity> kbs) {
        kbs.forEach(this::withLivePageCount);
        return kbs;
    }

    /**
     * 为单个知识库补充实时页面计数
     */
    private WikiKnowledgeBaseEntity withLivePageCount(WikiKnowledgeBaseEntity kb) {
        if (kb != null && kb.getId() != null) {
            kb.setPageCount(pageService.countByKbId(kb.getId()));
        }
        return kb;
    }
}
