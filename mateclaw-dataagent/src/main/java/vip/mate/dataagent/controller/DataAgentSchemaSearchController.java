package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vip.mate.common.result.R;
import vip.mate.dataagent.dto.SchemaSearchRequest;
import vip.mate.dataagent.dto.SchemaSearchResult;
import vip.mate.dataagent.service.SchemaEmbeddingService;

/**
 * Schema 语义检索控制器
 * <p>
 * 提供 Schema 向量化嵌入和语义检索 API。
 */
@RestController
@RequestMapping("/v1/schema-search")
@RequiredArgsConstructor
@Tag(name = "Schema 语义检索", description = "Schema 向量化嵌入与语义检索接口")
public class DataAgentSchemaSearchController {

    private final SchemaEmbeddingService schemaEmbeddingService;

    /**
     * 为数据源生成 Schema 嵌入
     */
    @PostMapping("/embed")
    @Operation(summary = "生成 Schema 嵌入", description = "为数据源的所有表生成 Schema 向量嵌入，用于语义检索")
    public R<Integer> embedSchema(
            @Parameter(description = "数据源 ID") @RequestParam Long datasourceId) {
        return R.ok(schemaEmbeddingService.embedSchema(datasourceId));
    }

    /**
     * 为单张表生成 Schema 嵌入
     */
    @PostMapping("/embed-table")
    @Operation(summary = "生成单表嵌入", description = "为指定表生成 Schema 向量嵌入")
    public R<Boolean> embedTable(
            @Parameter(description = "数据源 ID") @RequestParam Long datasourceId,
            @Parameter(description = "表名") @RequestParam String tableName) {
        return R.ok(schemaEmbeddingService.embedTable(datasourceId, tableName));
    }

    /**
     * 语义检索相关表
     */
    @PostMapping("/search")
    @Operation(summary = "语义检索", description = "基于自然语言查询检索相关的数据表，支持关键词/向量/混合检索")
    public R<SchemaSearchResult> search(@RequestBody SchemaSearchRequest request) {
        return R.ok(schemaEmbeddingService.searchSchema(request));
    }

    /**
     * 删除数据源的所有 Schema 嵌入
     */
    @DeleteMapping("/embed")
    @Operation(summary = "删除 Schema 嵌入", description = "删除数据源的所有 Schema 向量嵌入")
    public R<Void> deleteEmbed(
            @Parameter(description = "数据源 ID") @RequestParam Long datasourceId) {
        schemaEmbeddingService.deleteByDatasourceId(datasourceId);
        return R.ok(null);
    }

    /**
     * 预览表级嵌入文本
     */
    @GetMapping("/embedding-text")
    @Operation(summary = "预览嵌入文本", description = "预览指定表的 Schema 嵌入文本内容")
    public R<String> previewEmbeddingText(
            @Parameter(description = "数据源 ID") @RequestParam Long datasourceId,
            @Parameter(description = "表名") @RequestParam String tableName) {
        return R.ok(schemaEmbeddingService.buildEmbeddingText(datasourceId, tableName));
    }
}
