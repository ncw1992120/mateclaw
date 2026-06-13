package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vip.mate.common.result.R;
import vip.mate.dataagent.dto.SemanticModelCreateRequest;
import vip.mate.dataagent.dto.SemanticModelUpdateRequest;
import vip.mate.dataagent.dto.SemanticModelVO;
import vip.mate.dataagent.service.SemanticModelService;

import java.util.List;

/**
 * 字段级语义模型管理控制器
 * <p>
 * 提供语义模型的 CRUD、关键词搜索、自动初始化等 API。
 */
@RestController
@RequestMapping("/v1/semantic-models")
@RequiredArgsConstructor
@Tag(name = "语义模型管理", description = "字段级语义模型 CRUD、搜索与自动初始化接口")
public class DataAgentSemanticModelController {

    private final SemanticModelService semanticModelService;

    /**
     * 按数据源查询所有启用的语义模型
     */
    @GetMapping
    @Operation(summary = "查询语义模型列表", description = "按数据源 ID 查询所有启用的语义模型")
    public R<List<SemanticModelVO>> list(
            @Parameter(description = "数据源 ID") @RequestParam Long datasourceId,
            @Parameter(description = "表名（可选，多个用逗号分隔）") @RequestParam(required = false) String tableNames) {
        if (tableNames != null && !tableNames.isBlank()) {
            List<String> names = List.of(tableNames.split(","));
            return R.ok(semanticModelService.listByDatasourceIdAndTableNames(datasourceId, names));
        }
        return R.ok(semanticModelService.listByDatasourceId(datasourceId));
    }

    /**
     * 获取语义模型详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取语义模型详情", description = "根据 ID 获取语义模型详情")
    public R<SemanticModelVO> get(
            @Parameter(description = "语义模型 ID") @PathVariable Long id) {
        return R.ok(semanticModelService.getById(id));
    }

    /**
     * 创建语义模型
     */
    @PostMapping
    @Operation(summary = "创建语义模型", description = "为数据源字段创建业务语义映射")
    public R<SemanticModelVO> create(@RequestBody SemanticModelCreateRequest request) {
        return R.ok(semanticModelService.create(request));
    }

    /**
     * 更新语义模型
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新语义模型", description = "更新语义模型的业务语义信息")
    public R<SemanticModelVO> update(
            @Parameter(description = "语义模型 ID") @PathVariable Long id,
            @RequestBody SemanticModelUpdateRequest request) {
        return R.ok(semanticModelService.update(id, request));
    }

    /**
     * 删除语义模型
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除语义模型", description = "删除指定语义模型")
    public R<Void> delete(
            @Parameter(description = "语义模型 ID") @PathVariable Long id) {
        semanticModelService.delete(id);
        return R.ok(null);
    }

    /**
     * 启用语义模型
     */
    @PutMapping("/{id}/enable")
    @Operation(summary = "启用语义模型", description = "启用指定语义模型")
    public R<Void> enable(
            @Parameter(description = "语义模型 ID") @PathVariable Long id) {
        semanticModelService.enable(id);
        return R.ok(null);
    }

    /**
     * 停用语义模型
     */
    @PutMapping("/{id}/disable")
    @Operation(summary = "停用语义模型", description = "停用指定语义模型")
    public R<Void> disable(
            @Parameter(description = "语义模型 ID") @PathVariable Long id) {
        semanticModelService.disable(id);
        return R.ok(null);
    }

    /**
     * 关键词搜索语义模型
     */
    @GetMapping("/search")
    @Operation(summary = "关键词搜索", description = "在表名、列名、业务名、描述、同义词等字段中搜索")
    public R<List<SemanticModelVO>> search(
            @Parameter(description = "数据源 ID") @RequestParam Long datasourceId,
            @Parameter(description = "搜索关键词") @RequestParam String keyword) {
        return R.ok(semanticModelService.searchByKeyword(datasourceId, keyword));
    }

    /**
     * 从物理 Schema 自动初始化语义模型
     */
    @PostMapping("/auto-init")
    @Operation(summary = "自动初始化", description = "从物理 Schema 自动生成基础语义模型记录，仅创建不存在的记录")
    public R<Integer> autoInit(
            @Parameter(description = "数据源 ID") @RequestParam Long datasourceId) {
        return R.ok(semanticModelService.autoInitFromSchema(datasourceId));
    }

    /**
     * 从 Aloudata 指标平台同步语义模型
     */
    @PostMapping("/sync-aloudata")
    @Operation(summary = "从指标平台同步", description = "从 Aloudata 指标平台同步指标和维度的语义信息，仅创建不存在的记录，仅支持 aloudata 类型数据源")
    public R<Integer> syncFromAloudata(
            @Parameter(description = "数据源 ID（必须为 aloudata 类型）") @RequestParam Long datasourceId) {
        return R.ok(semanticModelService.syncFromAloudata(datasourceId));
    }
}
