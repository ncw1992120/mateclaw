package vip.mate.dataagent.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vip.mate.channel.web.Utf8SseEmitter;
import vip.mate.dataagent.exception.BusinessException;
import vip.mate.dataagent.service.DataAgentWikiService;
import vip.mate.sdk.service.WikiRuntime;
import vip.mate.wiki.dto.PageCitationWithRaw;
import vip.mate.wiki.dto.PageSearchResult;
import vip.mate.wiki.dto.RelatedPageResult;
import vip.mate.wiki.hotcache.HotCacheUpdateReason;
import vip.mate.wiki.job.event.WikiJobCreatedEvent;
import vip.mate.wiki.job.model.WikiProcessingJobEntity;
import vip.mate.wiki.model.WikiHotCacheEntity;
import vip.mate.wiki.model.WikiKnowledgeBaseEntity;
import vip.mate.wiki.model.WikiPageEntity;
import vip.mate.wiki.model.WikiRawMaterialEntity;
import vip.mate.wiki.model.WikiTransformationEntity;
import vip.mate.wiki.model.WikiTransformationRunEntity;
import vip.mate.wiki.sse.WikiProgressBus;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务知识库管理服务实现
 * <p>
 * 封装 Wiki 知识库相关的业务逻辑，通过 WikiRuntime 调用底层能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataAgentWikiServiceImpl implements DataAgentWikiService {

    private final WikiRuntime wiki;

    private final ObjectMapper objectMapper;

    private final ApplicationEventPublisher eventPublisher;

    // ==================== Knowledge Base ====================

    @Override
    public List<WikiKnowledgeBaseEntity> listKBs(Long workspaceId) {
        return withLivePageCount(wiki.listKBsByWorkspace(workspaceId));
    }

    @Override
    public List<WikiKnowledgeBaseEntity> listKBsByAgent(Long agentId, Long workspaceId) {
        List<WikiKnowledgeBaseEntity> kbs = wiki.listKBsByAgent(agentId);
        return withLivePageCount(kbs.stream()
                .filter(kb -> kb.getWorkspaceId() == null || kb.getWorkspaceId().equals(workspaceId))
                .toList());
    }

    @Override
    public WikiKnowledgeBaseEntity getKB(Long id) {
        WikiKnowledgeBaseEntity kb = wiki.getKB(id);
        if (kb == null) {
            return null;
        }
        return withLivePageCount(kb);
    }

    @Override
    public WikiKnowledgeBaseEntity createKB(String name, String description, Long agentId, Long workspaceId) {
        return wiki.createKB(name, description, agentId, workspaceId);
    }

    @Override
    public WikiKnowledgeBaseEntity updateKB(Long id, Map<String, Object> body) {
        String name = (String) body.get("name");
        String description = (String) body.get("description");
        wiki.updateKB(id, name, description);

        /* 更新绑定向量模型 */
        if (body.containsKey("embeddingModelId")) {
            Object v = body.get("embeddingModelId");
            Long embeddingModelId = null;
            if (v != null && !v.toString().isBlank()) {
                embeddingModelId = Long.valueOf(v.toString());
            }
            wiki.updateKBEmbeddingModelId(id, embeddingModelId);
        }
        return wiki.getKB(id);
    }

    @Override
    public Map<String, Object> deleteKB(Long id) {
        var result = wiki.deleteKB(id);
        return Map.of(
                "rawMaterialCount", result.rawMaterialCount(),
                "pageCount", result.pageCount(),
                "chunkCount", result.chunkCount(),
                "citationCount", result.citationCount(),
                "processingJobCount", result.processingJobCount(),
                "kbName", result.kbName()
        );
    }

    @Override
    public String getKBConfig(Long id) {
        return wiki.getKBConfig(id);
    }

    @Override
    public void updateKBConfig(Long id, String content) {
        wiki.updateKBConfig(id, content);
    }

    // ==================== Directory Scan ====================

    @Override
    public void setSourceDirectory(Long id, String path) {
        wiki.updateKBSourceDirectory(id, path);
    }

    @Override
    public Map<String, Object> scanDirectory(Long id) {
        var result = wiki.scanDirectory(id);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scanned", result.scanned());
        response.put("added", result.added());
        response.put("skipped", result.skipped());
        response.put("errors", result.errors());
        return response;
    }

    // ==================== Raw Materials ====================

    @Override
    public List<Map<String, Object>> listRawMaterials(Long kbId) {
        List<WikiRawMaterialEntity> raws = wiki.listRawMaterials(kbId);
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
            item.put("pageCount", wiki.countPagesByRawId(kbId, raw.getId()));
            result.add(item);
        }
        return result;
    }

    @Override
    public WikiRawMaterialEntity addRawText(Long kbId, String title, String content) {
        return wiki.addTextRaw(kbId, title, content);
    }

    @Override
    public WikiRawMaterialEntity uploadRaw(Long kbId, MultipartFile file) throws IOException {
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
            return wiki.addTextRaw(kbId, originalName, content);
        } else {
            Path uploadDir = Paths.get(wiki.getUploadDir()).toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);
            Path targetPath = uploadDir.resolve(System.currentTimeMillis() + "_" + originalName);
            file.transferTo(targetPath);
            return wiki.addFileRaw(kbId, originalName, sourceType,
                    file.getContentType(), targetPath.toString(), file.getSize());
        }
    }

    @Override
    public void deleteRaw(Long kbId, Long rawId) {
        WikiRawMaterialEntity raw = wiki.getRawMaterial(rawId);
        if (raw == null || !kbId.equals(raw.getKbId())) {
            throw new BusinessException(404, "原始材料不存在于该知识库");
        }
        wiki.deleteRawMaterial(rawId);
        wiki.decrementRawCount(kbId);
    }

    @Override
    public void reprocessRaw(Long kbId, Long rawId, boolean force) {
        WikiRawMaterialEntity raw = wiki.getRawMaterial(rawId);
        if (raw == null || !kbId.equals(raw.getKbId())) {
            throw new BusinessException(404, "原始材料不存在于该知识库");
        }
        if (force) {
            wiki.setRawLastProcessedHash(rawId, null);
        }
        wiki.reprocessRaw(rawId);
    }

    @Override
    public void cancelRaw(Long kbId, Long rawId) {
        WikiRawMaterialEntity raw = wiki.getRawMaterial(rawId);
        if (raw == null || !kbId.equals(raw.getKbId())) {
            throw new BusinessException(404, "原始材料不存在于该知识库");
        }
        wiki.requestCancelRaw(rawId);
    }

    @Override
    public ResponseEntity<Resource> downloadRaw(Long kbId, Long rawId) throws IOException {
        WikiRawMaterialEntity raw = wiki.getRawMaterial(rawId);
        if (raw == null || !kbId.equals(raw.getKbId())) {
            return ResponseEntity.notFound().build();
        }

        String rawTitle = raw.getTitle();
        String filename = (rawTitle != null && !rawTitle.isBlank()) ? rawTitle : ("source-" + rawId);

        Resource resource;
        long contentLength;
        MediaType mediaType;
        String sourceType = raw.getSourceType();

        if ("text".equals(sourceType)) {
            String content = raw.getOriginalContent();
            if (content == null) {
                return ResponseEntity.notFound().build();
            }
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            resource = new ByteArrayResource(bytes);
            contentLength = bytes.length;
            mediaType = MediaType.parseMediaType("text/plain;charset=UTF-8");
            if (!filename.contains(".")) {
                filename = filename + ".txt";
            }
        } else {
            String sourcePath = raw.getSourcePath();
            if (sourcePath == null || sourcePath.isBlank()) {
                return ResponseEntity.notFound().build();
            }
            Path path = Paths.get(sourcePath).toAbsolutePath().normalize();
            Path uploadDir = Paths.get(wiki.getUploadDir()).toAbsolutePath().normalize();
            if (!path.startsWith(uploadDir)) {
                log.warn("[Wiki] Download rejected: rawId={} path={} outside uploadDir={}", rawId, path, uploadDir);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            if (!Files.isRegularFile(path)) {
                return ResponseEntity.notFound().build();
            }
            resource = new FileSystemResource(path);
            contentLength = Files.size(path);
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        String asciiFallback = filename.replaceAll("[^\\x20-\\x7E]", "_")
                .replace("\"", "_").replace("\\", "_");
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        String contentDisposition = "attachment; filename=\"" + asciiFallback
                + "\"; filename*=UTF-8''" + encoded;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(contentLength)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(resource);
    }

    // ==================== Processing ====================

    @Override
    public Map<String, Object> processKB(Long kbId, boolean force) {
        int queued = wiki.processKB(kbId, force);
        return Map.of("queued", queued, "force", force);
    }

    @Override
    public Map<String, Object> getProcessingStatus(Long kbId) {
        WikiKnowledgeBaseEntity kb = wiki.getKB(kbId);
        if (kb == null) {
            throw new BusinessException(404, "知识库不存在");
        }

        List<WikiRawMaterialEntity> rawList = wiki.listRawMaterials(kbId);
        long pending = rawList.stream().filter(r -> "pending".equals(r.getProcessingStatus())).count();
        long processing = rawList.stream().filter(r -> "processing".equals(r.getProcessingStatus())).count();
        long completed = rawList.stream().filter(r -> "completed".equals(r.getProcessingStatus())).count();
        long failed = rawList.stream().filter(r -> "failed".equals(r.getProcessingStatus())).count();

        return Map.of(
                "status", kb.getStatus(),
                "pending", pending,
                "processing", processing,
                "completed", completed,
                "failed", failed,
                "totalRaw", rawList.size(),
                "totalPages", kb.getPageCount()
        );
    }

    @Override
    public SseEmitter subscribeProgress(Long kbId) {
        SseEmitter emitter = new Utf8SseEmitter(30L * 60 * 1000);
        wiki.subscribeProgress(kbId, emitter);

        emitter.onCompletion(() -> {
            wiki.unsubscribeProgress(kbId, emitter);
            log.debug("[Wiki SSE] emitter completed: kbId={}", kbId);
        });
        emitter.onTimeout(() -> {
            wiki.unsubscribeProgress(kbId, emitter);
            try { emitter.complete(); } catch (Exception ignore) { }
            log.debug("[Wiki SSE] emitter timeout: kbId={}", kbId);
        });
        emitter.onError(e -> {
            wiki.unsubscribeProgress(kbId, emitter);
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

    @Override
    public List<WikiPageEntity> listPages(Long kbId, Long rawId) {
        if (rawId != null) {
            return wiki.listPagesByRawId(kbId, rawId);
        }
        return wiki.listPages(kbId);
    }

    @Override
    public WikiPageEntity getPage(Long kbId, String slug) {
        return wiki.getPageBySlug(kbId, slug);
    }

    @Override
    public WikiPageEntity updatePage(Long kbId, String slug, String content, String summary) {
        return wiki.updatePageManually(kbId, slug, content, summary);
    }

    @Override
    public void deletePage(Long kbId, String slug) {
        wiki.deletePage(kbId, slug);
        wiki.setPageCount(kbId, wiki.countPages(kbId));
    }

    @Override
    public int batchDeletePages(Long kbId, List<String> slugs) {
        int deleted = wiki.batchDeletePages(kbId, slugs);
        wiki.setPageCount(kbId, wiki.countPages(kbId));
        return deleted;
    }

    @Override
    public List<WikiPageEntity> getBacklinks(Long kbId, String slug) {
        return wiki.getBacklinks(kbId, slug);
    }

    @Override
    public List<WikiPageEntity> listArchivedPages(Long kbId) {
        return wiki.listArchivedPages(kbId);
    }

    @Override
    public Map<String, Object> archivePage(Long kbId, String slug) {
        boolean changed = wiki.setPageArchived(kbId, slug, true);
        return Map.of("slug", slug, "archived", true, "changed", changed);
    }

    @Override
    public Map<String, Object> unarchivePage(Long kbId, String slug) {
        boolean changed = wiki.setPageArchived(kbId, slug, false);
        return Map.of("slug", slug, "archived", false, "changed", changed);
    }

    // ==================== Transformations ====================

    @Override
    public List<WikiTransformationEntity> listTransformations(Long kbId) {
        return wiki.listTransformations(kbId, 1L);
    }

    @Override
    public WikiTransformationEntity createTransformation(Long kbId, WikiTransformationEntity body) {
        body.setKbId(kbId);
        body.setWorkspaceId(1L);
        return wiki.createTransformation(body);
    }

    @Override
    public WikiTransformationEntity updateTransformation(Long id, WikiTransformationEntity body) {
        return wiki.updateTransformation(id, body);
    }

    @Override
    public void deleteTransformation(Long id) {
        wiki.deleteTransformation(id);
    }

    @Override
    public WikiTransformationRunEntity applyTransformation(Long id, Map<String, Object> body, boolean sync) {
        WikiTransformationEntity t = wiki.getTransformation(id);
        if (t == null) {
            throw new BusinessException(404, "转换模板不存在");
        }

        Object rawIdRaw = body == null ? null : body.get("rawId");
        Object pageIdRaw = body == null ? null : body.get("pageId");
        if (rawIdRaw == null && pageIdRaw == null) {
            throw new IllegalArgumentException("rawId 或 pageId 必须提供一个");
        }
        if (rawIdRaw != null && pageIdRaw != null) {
            throw new IllegalArgumentException("只能提供 rawId 或 pageId 中的一个");
        }

        if (rawIdRaw != null) {
            Long rawId = Long.valueOf(rawIdRaw.toString());
            if (sync) {
                return wiki.runTransformationOnRawSync(t, rawId, "manual");
            }
            wiki.runTransformationOnRawAsync(t, rawId, "manual");
        } else {
            Long pageId = Long.valueOf(pageIdRaw.toString());
            if (sync) {
                return wiki.runTransformationOnPageSync(t, pageId, "manual");
            }
            wiki.runTransformationOnPageAsync(t, pageId, "manual");
        }
        return null;
    }

    @Override
    public Map<String, Object> aggregateTransformation(Long id, Long kbId) {
        WikiTransformationEntity t = wiki.getTransformation(id);
        if (t == null) {
            throw new BusinessException(404, "转换模板不存在");
        }
        try {
            var res = wiki.aggregateTransformation(t, kbId, "manual");
            if (res.pageId() == null) {
                throw new IllegalStateException(res.title());
            }
            return Map.of(
                    "pageId", res.pageId(),
                    "slug", res.slug(),
                    "title", res.title(),
                    "sourcesUsed", res.sourcesUsed(),
                    "charsFed", res.charsFed(),
                    "created", res.created());
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        }
    }

    @Override
    public List<WikiTransformationRunEntity> listTransformationRuns(Long id) {
        return wiki.listTransformationRuns(id, 50);
    }

    @Override
    public boolean cancelTransformationRun(Long runId) {
        return wiki.cancelTransformationRun(runId);
    }

    @Override
    public Map<String, Object> saveRunAsPage(Long runId) {
        try {
            WikiPageEntity page = wiki.saveRunAsPage(runId);
            if (page == null) {
                throw new IllegalStateException("页面服务不可用");
            }
            return Map.of("pageId", page.getId(), "slug", page.getSlug(), "title", page.getTitle());
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        }
    }

    @Override
    public void deleteTransformationRun(Long runId) {
        wiki.deleteTransformationRun(runId);
    }

    // ==================== Hot Cache ====================

    @Override
    public WikiHotCacheEntity getHotCache(Long kbId) {
        return wiki.getHotCache(kbId).orElse(null);
    }

    @Override
    public void regenerateHotCache(Long kbId) {
        wiki.regenerateHotCache(kbId, HotCacheUpdateReason.MANUAL);
    }

    @Override
    public void resetHotCache(Long kbId) {
        wiki.resetHotCache(kbId);
    }

    // ==================== Relations / Stats / Jobs ====================

    @Override
    public List<RelatedPageResult> getRelatedPages(Long kbId, String slug, int topK) {
        return wiki.getRelatedPages(kbId, slug, Math.min(topK, 20));
    }

    @Override
    public List<PageCitationWithRaw> getPageCitations(Long kbId, Long pageId) {
        return wiki.getPageCitations(pageId);
    }

    @Override
    public List<WikiProcessingJobEntity> getJobs(Long kbId, Long rawId) {
        if (rawId != null) {
            return wiki.getLatestJobByRawId(rawId).map(List::of).orElse(List.of());
        }
        return wiki.listQueuedJobs(kbId, 20);
    }

    @Override
    public Map<String, Object> kbStats(Long kbId) {
        int pageCount = wiki.countPages(kbId);
        long enrichedCount = wiki.listPagesWithContent(kbId).stream()
                .filter(p -> p.getContent() != null && p.getContent().contains("[["))
                .count();
        var allJobs = wiki.listJobsByKbId(kbId, 200);
        Map<Long, WikiProcessingJobEntity> latestByRaw = new HashMap<>();
        for (var job : allJobs) {
            latestByRaw.merge(job.getRawId(), job,
                    (a, b) -> a.getId() >= b.getId() ? a : b);
        }
        int failedJobCount = (int) latestByRaw.values().stream()
                .filter(j -> "failed".equals(j.getStatus())).count();
        int runningJobCount = (int) latestByRaw.values().stream()
                .filter(j -> "running".equals(j.getStatus())).count();
        var drift = wiki.getEmbeddingDrift(kbId);
        return Map.of(
                "pageCount", pageCount,
                "enrichedPageCount", enrichedCount,
                "failedJobCount", failedJobCount,
                "runningJobCount", runningJobCount,
                "embeddingDrift", drift);
    }

    @Override
    public Map<String, Object> enrichPage(Long kbId, String slug) {
        WikiPageEntity page = wiki.getPageBySlug(kbId, slug);
        if (page == null) {
            throw new IllegalArgumentException("页面不存在: " + slug);
        }
        Long rawId = resolveFirstRawId(page);
        var job = wiki.createLightEnrichJob(kbId, rawId);
        eventPublisher.publishEvent(new WikiJobCreatedEvent(job.getId()));
        return Map.of("jobId", job.getId());
    }

    @Override
    public Map<String, Object> repairPage(Long kbId, String slug) {
        WikiPageEntity page = wiki.getPageBySlug(kbId, slug);
        if (page == null) {
            throw new IllegalArgumentException("页面不存在: " + slug);
        }
        Long rawId = resolveFirstRawId(page);
        var job = wiki.createLocalRepairJob(kbId, rawId, page.getId());
        eventPublisher.publishEvent(new WikiJobCreatedEvent(job.getId()));
        return Map.of("jobId", job.getId());
    }

    @Override
    public List<PageSearchResult> searchPreview(Long kbId, Map<String, Object> body) {
        String query = (String) body.getOrDefault("query", "");
        String mode = (String) body.getOrDefault("mode", "hybrid");
        int topK = body.containsKey("topK") ? ((Number) body.get("topK")).intValue() : 5;
        return wiki.search(kbId, query, mode, Math.min(topK, 20));
    }

    // ==================== Private Helpers ====================

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
            kb.setPageCount(wiki.countPages(kb.getId()));
        }
        return kb;
    }

    /**
     * 解析页面的第一个 sourceRawId
     */
    private Long resolveFirstRawId(WikiPageEntity page) {
        Long rawId = 0L;
        try {
            List<Long> rawIds = objectMapper.readValue(
                    page.getSourceRawIds() != null ? page.getSourceRawIds() : "[]",
                    new TypeReference<List<Long>>() {});
            if (!rawIds.isEmpty()) {
                rawId = rawIds.get(0);
            }
        } catch (Exception ignored) { }
        return rawId;
    }
}
