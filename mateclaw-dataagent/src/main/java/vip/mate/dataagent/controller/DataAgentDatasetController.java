package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vip.mate.common.result.R;
import vip.mate.dataagent.auth.annotation.RequireWorkspaceRole;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.*;
import vip.mate.dataagent.service.DatasetManageService;
import vip.mate.dataagent.service.DatasetExecutionService;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.dataset.*;

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
    private final DatasetExecutionService executionService;
    private final WorkspaceGuard workspaceGuard;
    private final vip.mate.dataagent.service.QueryPlanner queryPlanner;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @GetMapping("/{id}/descriptor")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "获取数据集输入描述", description = "返回 Python/预览使用的字段和来源类型，不返回连接凭据")
    public R<DatasetInputDescriptor> descriptor(@PathVariable Long id, @RequestParam(defaultValue = "dataset") String inputName) {
        DatasetAccessContext context = new DatasetAccessContext(workspaceGuard.currentWorkspaceId(), workspaceGuard.currentUserId(), "preview-" + id, java.util.Set.of(id));
        return R.ok(executionService.descriptor(context, id, inputName));
    }

    @PostMapping("/preview")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "预览统一数据集", description = "通过统一 DatasetSourceAdapter 读取并返回受控预览")
    public R<DatasetBatch> preview(@RequestBody DatasetReadRequest request) {
        DatasetAccessContext context = new DatasetAccessContext(workspaceGuard.currentWorkspaceId(), workspaceGuard.currentUserId(), "preview-" + request.datasetId(), java.util.Set.of(request.datasetId()));
        return R.ok(executionService.preview(context, request));
    }

    /**
     * 查询计划预览：合并查询配置草稿与运行时 QueryContext，返回计划、描述符、受控行集与下推报告。
     * 预览不落库；显式空集合短路时不访问数据源。
     */
    @PostMapping("/query-plan/preview")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "查询计划预览", description = "编辑器验证单个数据集查询计划，返回 plan/descriptor/rows/pushdownReport")
    public R<Map<String, Object>> queryPlanPreview(@RequestBody QueryPlanPreviewRequest request) {
        DatasetAccessContext context = new DatasetAccessContext(workspaceGuard.currentWorkspaceId(),
                workspaceGuard.currentUserId(), "plan-preview-" + request.datasetId(), java.util.Set.of(request.datasetId()));
        DatasetInputDescriptor descriptor = executionService.descriptor(context, request.datasetId(), request.inputName());

        com.fasterxml.jackson.databind.node.ObjectNode input = objectMapper.createObjectNode();
        input.put("datasetId", String.valueOf(request.datasetId()));
        input.put("inputName", request.inputName());
        if (request.queryConfig() != null && request.queryConfig().isObject()) {
            input.set("queryConfig", request.queryConfig());
        }
        com.fasterxml.jackson.databind.node.ObjectNode component = objectMapper.createObjectNode();
        component.putObject("config").putObject("datasetPipeline")
                .putArray("datasetInputs").add(input);

        vip.mate.dataagent.dto.DatasetQueryPlanDTO plan =
                queryPlanner.plan(component, input, request.queryContext(), false);

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("plan", plan);
        response.put("descriptor", descriptor);
        response.put("pushdownReport", null);
        if (plan.shortCircuitEmpty()) {
            // 显式空集合：短路空结果，不访问数据源
            response.put("rows", List.of());
            response.put("rowCount", 0);
            response.put("hasNext", false);
            return R.ok(response);
        }

        Map<String, String> fieldRoles = new java.util.HashMap<>();
        descriptor.schema().forEach(column -> fieldRoles.put(column.name(), column.semanticRole()));
        List<DatasetFilter> filters = plan.filters().stream()
                .map(f -> new DatasetFilter(f.field(), fieldRoles.getOrDefault(f.field(), "dimension"), f.operator(), f.value()))
                .toList();
        List<DatasetSort> sorts = plan.orders().stream()
                .map(o -> new DatasetSort(o.field(), o.direction()))
                .toList();
        Integer limit = plan.pagination() == null ? 100 : plan.pagination().pageSize();
        Integer offset = plan.pagination() == null ? 0 : plan.pagination().offset();
        boolean requestTotal = plan.pagination() != null;
        DatasetReadRequest readRequest = new DatasetReadRequest(request.datasetId(), request.inputName(),
                plan.columns(), filters, sorts, limit, offset, Map.of(), requestTotal);
        DatasetBatch batch = executionService.preview(context, readRequest);
        response.put("rows", batch.rows());
        response.put("rowCount", batch.rowCount());
        // totalCount 对旧版 Aloudata 接口可能缺失；hasNext 始终返回，供前端无总数分页使用。
        response.put("hasNext", !batch.last());
        response.put("pushdownReport", batch.pushdownReport());
        if (batch.totalCount() != null) response.put("totalCount", batch.totalCount());
        return R.ok(response);
    }

    /**
     * 数据集列表
     */
    @GetMapping
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "数据集列表", description = "获取所有数据集")
    public R<List<DatasetVO>> list() {
        return R.ok(datasetService.listDatasets());
    }

    /**
     * 数据集详情
     */
    @GetMapping("/{id}")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "数据集详情", description = "根据 ID 获取数据集详情（含字段列表）")
    public R<DatasetVO> get(
            @Parameter(description = "数据集 ID") @PathVariable Long id) {
        return R.ok(datasetService.getDataset(id));
    }

    /**
     * 创建数据集
     */
    @PostMapping
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    @Operation(summary = "创建数据集", description = "创建数据集，关联数据源表并自动提取字段信息")
    public R<DatasetVO> create(@RequestBody DatasetCreateRequest request) {
        return R.ok(datasetService.createDataset(request));
    }

    /**
     * 更新数据集
     */
    @PutMapping("/{id}")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
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
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
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
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "获取字段列表", description = "获取数据集的所有字段定义")
    public R<List<DatasetFieldVO>> listFields(
            @Parameter(description = "数据集 ID") @PathVariable Long datasetId) {
        return R.ok(datasetService.listFields(datasetId));
    }

    /**
     * 获取数据集数据（分页）
     */
    @GetMapping("/{datasetId}/data")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
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
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
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
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
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
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
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
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
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
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    @Operation(summary = "同步数据", description = "从源数据表拉取数据并持久化到本地业务数据表")
    public R<DatasetVO> syncData(
            @Parameter(description = "数据集 ID") @PathVariable Long datasetId) {
        return R.ok(datasetService.syncDatasetData(datasetId));
    }
}
