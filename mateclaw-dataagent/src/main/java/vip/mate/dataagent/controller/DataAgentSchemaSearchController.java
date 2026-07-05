package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vip.mate.common.result.R;
import vip.mate.dataagent.auth.annotation.RequireGlobalAdmin;
import vip.mate.dataagent.auth.annotation.RequireWorkspaceRole;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.SchemaSearchRequest;
import vip.mate.dataagent.dto.SchemaSearchResult;
import vip.mate.dataagent.service.DatasourceManageService;
import vip.mate.dataagent.service.SchemaEmbeddingService;

/**
 * Schema 语义检索控制器
 * <p>
 * 提供 Schema 向量化嵌入和语义检索 API。
 * 通过 datasource_id 级联校验工作区归属，确保跨工作区越权访问被拦截。
 */
@RestController
@RequestMapping("/v1/schema-search")
@RequiredArgsConstructor
@Tag(name = "Schema 语义检索", description = "Schema 向量化嵌入与语义检索接口")
public class DataAgentSchemaSearchController {

    private final SchemaEmbeddingService schemaEmbeddingService;
    private final DatasourceManageService datasourceManageService;

    /**
     * 校验当前用户对数据源是否具有可读权限（owner / meta_shared / 资源授权）
     * <p>
     * 通过 DatasourceManageService.checkDatasourceReadable 触发可读性校验（404/403 自动抛出）。
     * 共享数据源（meta_shared=true）对同工作区所有用户可读，用于元数据查询场景。
     */
    private void requireDatasourceReadable(Long datasourceId) {
        datasourceManageService.checkDatasourceReadable(datasourceId);
    }

    /**
     * 为数据源生成 Schema 嵌入
     */
    @PostMapping("/embed")
    @RequireGlobalAdmin
    @Operation(summary = "生成 Schema 嵌入", description = "为数据源的所有表生成 Schema 向量嵌入，用于语义检索。仅全局管理员可执行同步元数据操作")
    public R<Integer> embedSchema(
            @Parameter(description = "数据源 ID") @RequestParam Long datasourceId) {
        requireDatasourceReadable(datasourceId);
        return R.ok(schemaEmbeddingService.embedSchema(datasourceId));
    }

    /**
     * 为单张表生成 Schema 嵌入
     */
    @PostMapping("/embed-table")
    @RequireGlobalAdmin
    @Operation(summary = "生成单表嵌入", description = "为指定表生成 Schema 向量嵌入。仅全局管理员可执行同步元数据操作")
    public R<Boolean> embedTable(
            @Parameter(description = "数据源 ID") @RequestParam Long datasourceId,
            @Parameter(description = "表名") @RequestParam String tableName) {
        requireDatasourceReadable(datasourceId);
        return R.ok(schemaEmbeddingService.embedTable(datasourceId, tableName));
    }

    /**
     * 语义检索相关表
     */
    @PostMapping("/search")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "语义检索", description = "基于自然语言查询检索相关的数据表，支持关键词/向量/混合检索")
    public R<SchemaSearchResult> search(@RequestBody SchemaSearchRequest request) {
        requireDatasourceReadable(request.getDatasourceId());
        return R.ok(schemaEmbeddingService.searchSchema(request));
    }

    /**
     * 删除数据源的所有 Schema 嵌入
     */
    @DeleteMapping("/embed")
    @RequireGlobalAdmin
    @Operation(summary = "删除 Schema 嵌入", description = "删除数据源的所有 Schema 向量嵌入。仅全局管理员可执行同步元数据操作")
    public R<Void> deleteEmbed(
            @Parameter(description = "数据源 ID") @RequestParam Long datasourceId) {
        requireDatasourceReadable(datasourceId);
        schemaEmbeddingService.deleteByDatasourceId(datasourceId);
        return R.ok(null);
    }

    /**
     * 预览表级嵌入文本
     */
    @GetMapping("/embedding-text")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "预览嵌入文本", description = "预览指定表的 Schema 嵌入文本内容")
    public R<String> previewEmbeddingText(
            @Parameter(description = "数据源 ID") @RequestParam Long datasourceId,
            @Parameter(description = "表名") @RequestParam String tableName) {
        requireDatasourceReadable(datasourceId);
        return R.ok(schemaEmbeddingService.buildEmbeddingText(datasourceId, tableName));
    }
}
