package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vip.mate.common.result.R;
import vip.mate.dataagent.dto.*;
import vip.mate.dataagent.service.DatasetManageService;

import java.util.List;
import java.util.Map;

/**
 * 数据集管理控制器
 * <p>
 * 提供数据集 CRUD、数据查询、行级操作 API。
 */
@RestController
@RequestMapping("/v1/datasets")
@RequiredArgsConstructor
@Tag(name = "数据集管理", description = "数据集 CRUD、数据查询与行级操作接口")
public class DataAgentDatasetController {

    private final DatasetManageService datasetService;

    /**
     * 数据集列表
     */
    @GetMapping
    @Operation(summary = "数据集列表", description = "获取所有数据集")
    public R<List<DatasetVO>> list() {
        return R.ok(datasetService.listDatasets());
    }

    /**
     * 数据集详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "数据集详情", description = "根据 ID 获取数据集详情（含字段列表）")
    public R<DatasetVO> get(
            @Parameter(description = "数据集 ID") @PathVariable Long id) {
        return R.ok(datasetService.getDataset(id));
    }

    /**
     * 创建数据集
     */
    @PostMapping
    @Operation(summary = "创建数据集", description = "创建数据集，关联数据源表并自动提取字段信息")
    public R<DatasetVO> create(@RequestBody DatasetCreateRequest request) {
        return R.ok(datasetService.createDataset(request));
    }

    /**
     * 更新数据集
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新数据集", description = "更新数据集基本信息")
    public R<DatasetVO> update(
            @Parameter(description = "数据集 ID") @PathVariable Long id,
            @RequestBody DatasetUpdateRequest request) {
        return R.ok(datasetService.updateDataset(id, request));
    }

    /**
     * 删除数据集
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除数据集", description = "删除指定数据集及其字段配置")
    public R<Void> delete(
            @Parameter(description = "数据集 ID") @PathVariable Long id) {
        datasetService.deleteDataset(id);
        return R.ok(null);
    }

    /**
     * 获取数据集字段列表
     */
    @GetMapping("/{datasetId}/fields")
    @Operation(summary = "获取字段列表", description = "获取数据集的所有字段定义")
    public R<List<DatasetFieldVO>> listFields(
            @Parameter(description = "数据集 ID") @PathVariable Long datasetId) {
        return R.ok(datasetService.listFields(datasetId));
    }

    /**
     * 获取数据集数据（分页）
     */
    @GetMapping("/{datasetId}/data")
    @Operation(summary = "获取数据集数据", description = "分页查询数据集数据，支持类 Excel 表格展示")
    public R<DatasetDataVO> getDatasetData(
            @Parameter(description = "数据集 ID") @PathVariable Long datasetId,
            @Parameter(description = "页码（从1开始）") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "50") int size) {
        return R.ok(datasetService.getDatasetData(datasetId, page, size));
    }

    /**
     * 更新数据集行数据
     */
    @PutMapping("/{datasetId}/rows")
    @Operation(summary = "更新行数据", description = "更新数据集中的某一行数据")
    public R<Void> updateRow(
            @Parameter(description = "数据集 ID") @PathVariable Long datasetId,
            @RequestBody DatasetRowUpdateRequest request) {
        datasetService.updateRow(datasetId, request);
        return R.ok(null);
    }

    /**
     * 新增数据集行
     */
    @PostMapping("/{datasetId}/rows")
    @Operation(summary = "新增行数据", description = "在数据集中新增一行数据")
    public R<Void> addRow(
            @Parameter(description = "数据集 ID") @PathVariable Long datasetId,
            @RequestBody DatasetRowCreateRequest request) {
        datasetService.addRow(datasetId, request);
        return R.ok(null);
    }

    /**
     * 删除数据集行
     */
    @DeleteMapping("/{datasetId}/rows")
    @Operation(summary = "删除行数据", description = "删除数据集中的某一行数据")
    public R<Void> deleteRow(
            @Parameter(description = "数据集 ID") @PathVariable Long datasetId,
            @RequestBody Map<String, Object> rowKey) {
        datasetService.deleteRow(datasetId, rowKey);
        return R.ok(null);
    }

    /**
     * 更新字段分类
     */
    @PutMapping("/fields/{fieldId}/category")
    @Operation(summary = "更新字段分类", description = "更新字段的分类（维度/度量）")
    public R<DatasetFieldVO> updateFieldCategory(
            @Parameter(description = "字段 ID") @PathVariable Long fieldId,
            @RequestBody Map<String, String> body) {
        String category = body.get("fieldCategory");
        return R.ok(datasetService.updateFieldCategory(fieldId, category));
    }

    /**
     * 同步数据集数据（从源表拉取数据并落库到本地业务数据表）
     */
    @PostMapping("/{datasetId}/sync")
    @Operation(summary = "同步数据", description = "从源数据表拉取数据并持久化到本地业务数据表")
    public R<DatasetVO> syncData(
            @Parameter(description = "数据集 ID") @PathVariable Long datasetId) {
        return R.ok(datasetService.syncDatasetData(datasetId));
    }
}
