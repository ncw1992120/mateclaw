package vip.mate.dataagent.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vip.mate.common.result.R;
import vip.mate.dataagent.auth.annotation.RequireWorkspaceRole;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.aloudata.AloudataApiProperties.ApiEndpoint;
import vip.mate.dataagent.aloudata.AloudataEndpointService;
import vip.mate.dataagent.dto.*;
import vip.mate.dataagent.model.AloudataCategoryEntity;
import vip.mate.dataagent.service.AloudataSemanticSyncService;
import vip.mate.dataagent.service.DatasourceManageService;

import java.util.List;
import java.util.Map;

/**
 * 数据源管理控制器
 * <p>
 * 提供数据源 CRUD、连接测试、启停切换、动态 Schema 发现 API。
 */
@RestController
@RequestMapping("/v1/datasources")
@RequiredArgsConstructor
@Tag(name = "数据源管理", description = "数据源 CRUD、连接测试与动态 Schema 发现接口")
public class DataAgentDatasourceController {

    private final DatasourceManageService datasourceService;
    private final AloudataEndpointService aloudataEndpointService;
    private final AloudataSemanticSyncService aloudataSyncService;
    private final WorkspaceGuard workspaceGuard;

    /**
     * 数据源列表
     * <p>
     * 按当前登录用户 ID 过滤，仅返回该用户创建的数据源。
     * 管理员也不可查看他人配置的数据源（第一性原理：数据源归属创建者）。
     */
    @GetMapping
    @Operation(summary = "数据源列表", description = "获取当前用户创建的数据源列表，按 owner_id 过滤")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    public R<List<DatasourceVO>> list() {
        Long userId = workspaceGuard.currentUserId();
        return R.ok(datasourceService.listDatasources(userId));
    }

    /**
     * 数据源详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "数据源详情", description = "根据 ID 获取数据源详情")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    public R<DatasourceVO> get(
            @Parameter(description = "数据源 ID") @PathVariable Long id) {
        return R.ok(datasourceService.getDatasource(id));
    }

    /**
     * 创建数据源
     * <p>
     * 创建时自动填充 ownerId 为当前登录用户 ID。
     */
    @PostMapping
    @Operation(summary = "创建数据源", description = "新增数据源配置，owner_id 自动填充为当前登录用户")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    public R<DatasourceVO> create(@RequestBody DatasourceCreateRequest request) {
        Long userId = workspaceGuard.currentUserId();
        return R.ok(datasourceService.createDatasource(request, userId));
    }

    /**
     * 更新数据源
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新数据源", description = "更新数据源配置")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    public R<DatasourceVO> update(
            @Parameter(description = "数据源 ID") @PathVariable Long id,
            @RequestBody DatasourceUpdateRequest request) {
        return R.ok(datasourceService.updateDatasource(id, request));
    }

    /**
     * 删除数据源
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除数据源", description = "删除指定数据源及其关联的表和字段元数据")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    public R<Void> delete(
            @Parameter(description = "数据源 ID") @PathVariable Long id) {
        datasourceService.deleteDatasource(id);
        return R.ok(null);
    }

    /**
     * 测试连接（基于已有数据源 ID）
     */
    @PostMapping("/{id}/test")
    @Operation(summary = "测试连接", description = "测试数据源连接是否可用")
    public R<Boolean> testConnection(
            @Parameter(description = "数据源 ID") @PathVariable Long id) {
        return R.ok(datasourceService.testConnection(id));
    }

    /**
     * 测试连接（不创建数据源记录，仅做连通性测试）
     */
    @PostMapping("/test")
    @Operation(summary = "测试连接（预览）", description = "使用连接参数测试数据源连通性，不持久化数据源记录")
    public R<Boolean> testConnectionByParams(
            @RequestBody DatasourceCreateRequest request) {
        return R.ok(datasourceService.testConnectionByParams(request));
    }

    /**
     * 启停切换
     */
    @PutMapping("/{id}/toggle")
    @Operation(summary = "启停切换", description = "启用或禁用数据源")
    public R<DatasourceVO> toggle(
            @Parameter(description = "数据源 ID") @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("enabled");
        return R.ok(datasourceService.toggleDatasource(id, enabled != null && enabled));
    }

    /**
     * 触发 Schema 发现（仅管理员可操作）
     */
    @PostMapping("/{id}/schema-discovery")
    @Operation(summary = "触发 Schema 发现", description = "自动扫描数据源，提取表结构、字段类型、主外键关系、索引信息。仅工作区管理员可执行同步元数据操作")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    public R<DatasourceVO> triggerSchemaDiscovery(
            @Parameter(description = "数据源 ID") @PathVariable Long id) {
        return R.ok(datasourceService.triggerSchemaDiscovery(id));
    }

    /**
     * 获取数据源下的表列表
     */
    @GetMapping("/{datasourceId}/tables")
    @Operation(summary = "获取表列表", description = "获取数据源下的所有表元数据")
    public R<List<DatasourceTableVO>> listTables(
            @Parameter(description = "数据源 ID") @PathVariable Long datasourceId) {
        return R.ok(datasourceService.listTables(datasourceId));
    }

    /**
     * 获取表详情（含字段列表）
     */
    @GetMapping("/{datasourceId}/tables/{tableId}")
    @Operation(summary = "获取表详情", description = "获取表详情，包含字段列表、主外键关系和索引信息")
    public R<DatasourceTableVO> getTableDetail(
            @Parameter(description = "数据源 ID") @PathVariable Long datasourceId,
            @Parameter(description = "表 ID") @PathVariable Long tableId) {
        return R.ok(datasourceService.getTableDetail(datasourceId, tableId));
    }

    /**
     * 获取表字段列表
     */
    @GetMapping("/{datasourceId}/tables/{tableId}/columns")
    @Operation(summary = "获取字段列表", description = "获取表下的所有字段元数据")
    public R<List<DatasourceColumnVO>> listColumns(
            @Parameter(description = "数据源 ID") @PathVariable Long datasourceId,
            @Parameter(description = "表 ID") @PathVariable Long tableId) {
        return R.ok(datasourceService.listColumns(datasourceId, tableId));
    }

    /**
     * 同步单张表元数据（仅管理员可操作）
     */
    @PostMapping("/{datasourceId}/tables/{tableId}/sync")
    @Operation(summary = "同步单张表元数据", description = "重新同步指定表的字段信息，支持追加和覆盖模式。仅工作区管理员可执行同步元数据操作")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    public R<DatasourceTableVO> syncTable(
            @Parameter(description = "数据源 ID") @PathVariable Long datasourceId,
            @Parameter(description = "表 ID") @PathVariable Long tableId,
            @RequestBody(required = false) TableSyncRequest request) {
        String mode = (request != null && request.getMode() != null) ? request.getMode() : "append";
        return R.ok(datasourceService.syncSingleTable(datasourceId, tableId, mode));
    }

    /**
     * 预览表数据
     */
    @GetMapping("/{datasourceId}/tables/{tableId}/preview")
    @Operation(summary = "预览表数据", description = "获取表数据预览，默认返回前100行")
    public R<TableDataPreviewVO> previewTableData(
            @Parameter(description = "数据源 ID") @PathVariable Long datasourceId,
            @Parameter(description = "表 ID") @PathVariable Long tableId,
            @Parameter(description = "返回行数限制") @RequestParam(defaultValue = "100") int limit) {
        return R.ok(datasourceService.previewTableData(datasourceId, tableId, limit));
    }

    /**
     * 删除数据源下的表
     */
    @DeleteMapping("/{datasourceId}/tables/{tableId}")
    @Operation(summary = "删除表元数据", description = "删除数据源下的指定表及其字段元数据")
    public R<Void> deleteTable(
            @Parameter(description = "数据源 ID") @PathVariable Long datasourceId,
            @Parameter(description = "表 ID") @PathVariable Long tableId) {
        datasourceService.deleteTable(datasourceId, tableId);
        return R.ok(null);
    }

    // ==================== Aloudata 指标平台相关接口 ====================

    /**
     * 触发 Aloudata 语义层全量同步（仅管理员可操作）
     */
    @PostMapping("/{datasourceId}/aloudata/sync")
    @Operation(summary = "同步 Aloudata 语义层", description = "从 Aloudata 指标平台同步指标、维度、类目元数据到本地语义层（MySQL + ES）。仅工作区管理员可执行同步元数据操作")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    public R<AloudataSemanticSyncService.SyncResult> syncAloudataSemantic(
            @Parameter(description = "数据源 ID") @PathVariable Long datasourceId) {
        return R.ok(aloudataSyncService.fullSync(datasourceId));
    }

    /**
     * 重建 ES 索引（仅管理员可操作）
     */
    @PostMapping("/{datasourceId}/aloudata/rebuild-es")
    @Operation(summary = "重建 ES 索引", description = "将已同步到 MySQL 的指标和维度数据向量化并写入 ES 索引，不从 Aloudata API 重新拉取。适用于 ES 索引损坏重建、EmbeddingModel 切换后重新向量化等场景。仅工作区管理员可执行")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    public R<AloudataSemanticSyncService.SyncResult> rebuildEsIndex(
            @Parameter(description = "数据源 ID") @PathVariable Long datasourceId) {
        return R.ok(aloudataSyncService.rebuildEsIndex(datasourceId));
    }

    /**
     * 查询 Aloudata 语义层同步状态
     */
    @GetMapping("/{datasourceId}/aloudata/sync-status")
    @Operation(summary = "查询同步状态", description = "查询 Aloudata 语义层的同步状态和已同步数量")
    public R<AloudataSemanticSyncService.SyncResult> getAloudataSyncStatus(
            @Parameter(description = "数据源 ID") @PathVariable Long datasourceId) {
        return R.ok(aloudataSyncService.getSyncStatus(datasourceId));
    }

    /**
     * 查询已同步的指标列表（分页）
     */
    @GetMapping("/{datasourceId}/aloudata/synced-metrics")
    @Operation(summary = "已同步指标列表", description = "获取已同步到本地语义层的 Aloudata 指标列表（分页）")
    public R<List<AloudataMetricSemanticDTO>> listSyncedMetrics(
            @Parameter(description = "数据源 ID") @PathVariable Long datasourceId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNumber,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(aloudataSyncService.listSyncedMetrics(datasourceId, pageNumber, pageSize));
    }

    /**
     * 查询已同步的维度列表（分页）
     */
    @GetMapping("/{datasourceId}/aloudata/synced-dimensions")
    @Operation(summary = "已同步维度列表", description = "获取已同步到本地语义层的 Aloudata 维度列表（分页）")
    public R<List<AloudataDimensionSemanticDTO>> listSyncedDimensions(
            @Parameter(description = "数据源 ID") @PathVariable Long datasourceId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNumber,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(aloudataSyncService.listSyncedDimensions(datasourceId, pageNumber, pageSize));
    }

    /**
     * 查询指标关联的维度列表
     */
    @GetMapping("/{datasourceId}/aloudata/metrics/{metricName}/dimensions")
    @Operation(summary = "指标可用维度", description = "查询指定指标关联的可用维度名称列表")
    public R<List<String>> listMetricDimensions(
            @Parameter(description = "数据源 ID") @PathVariable Long datasourceId,
            @Parameter(description = "指标英文名") @PathVariable String metricName) {
        return R.ok(aloudataSyncService.listMetricDimensions(datasourceId, metricName));
    }

    /**
     * 查询指标关联的维度详情列表
     */
    @GetMapping("/{datasourceId}/aloudata/metrics/{metricName}/dimension-details")
    @Operation(summary = "指标可用维度详情", description = "查询指定指标关联的可用维度详情列表，包含维度名称、展示名、描述等")
    public R<List<AloudataDimensionSemanticDTO>> listMetricDimensionDetails(
            @Parameter(description = "数据源 ID") @PathVariable Long datasourceId,
            @Parameter(description = "指标英文名") @PathVariable String metricName) {
        return R.ok(aloudataSyncService.listMetricDimensionDetails(datasourceId, metricName));
    }

    /**
     * 查询维度关联的指标详情列表
     */
    @GetMapping("/{datasourceId}/aloudata/dimensions/{dimName}/metric-details")
    @Operation(summary = "维度关联指标详情", description = "查询指定维度关联的指标详情列表，包含指标名称、展示名、业务口径等")
    public R<List<AloudataMetricSemanticDTO>> listDimensionMetricDetails(
            @Parameter(description = "数据源 ID") @PathVariable Long datasourceId,
            @Parameter(description = "维度英文名") @PathVariable String dimName) {
        return R.ok(aloudataSyncService.listDimensionMetricDetails(datasourceId, dimName));
    }

    /**
     * 查询已同步的类目列表
     */
    @GetMapping("/{datasourceId}/aloudata/synced-categories")
    @Operation(summary = "已同步类目列表", description = "获取已同步到本地语义层的 Aloudata 类目列表，支持按类型过滤")
    public R<List<AloudataCategoryEntity>> listSyncedCategories(
            @Parameter(description = "数据源 ID") @PathVariable Long datasourceId,
            @Parameter(description = "类目类型过滤（可选）：CATEGORY_METRIC / CATEGORY_DIMENSION") @RequestParam(required = false) String categoryType) {
        return R.ok(aloudataSyncService.listSyncedCategories(datasourceId, categoryType));
    }

    /**
     * 分页查询已同步的指标列表
     */
    @GetMapping("/{datasourceId}/aloudata/metrics/page")
    @Operation(summary = "分页查询指标列表", description = "分页查询已同步的指标，支持关键词搜索和类目过滤")
    public R<IPage<AloudataMetricSemanticDTO>> pageMetrics(
            @Parameter(description = "数据源 ID") @PathVariable Long datasourceId,
            @Parameter(description = "分页查询参数") AloudataMetricPageQuery query) {
        return R.ok(aloudataSyncService.pageMetrics(datasourceId, query));
    }

    /**
     * 分页查询已同步的维度列表
     */
    @GetMapping("/{datasourceId}/aloudata/dimensions/page")
    @Operation(summary = "分页查询维度列表", description = "分页查询已同步的维度，支持关键词搜索和类目过滤")
    public R<IPage<AloudataDimensionSemanticDTO>> pageDimensions(
            @Parameter(description = "数据源 ID") @PathVariable Long datasourceId,
            @Parameter(description = "分页查询参数") AloudataDimensionPageQuery query) {
        return R.ok(aloudataSyncService.pageDimensions(datasourceId, query));
    }

    /**
     * 按指标类目分组查询指标列表
     */
    @GetMapping("/{datasourceId}/aloudata/metrics/grouped")
    @Operation(summary = "按类目分组指标列表",
            description = "获取按指标类目分组的指标列表，支持关键词搜索、类目过滤和每类目数量限制")
    public R<List<MetricCategoryGroupDTO>> listMetricsGroupByCategory(
            @Parameter(description = "数据源 ID") @PathVariable Long datasourceId,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "类目 ID 过滤") @RequestParam(required = false) String categoryId,
            @Parameter(description = "每类目最大返回条数，小于等于 0 表示不限制") @RequestParam(required = false, defaultValue = "0") int limitPerCategory) {
        return R.ok(aloudataSyncService.listMetricsGroupByCategory(datasourceId, keyword, categoryId, limitPerCategory));
    }

    /**
     * 按维度类目分组查询维度列表
     */
    @GetMapping("/{datasourceId}/aloudata/dimensions/grouped")
    @Operation(summary = "按类目分组维度列表",
            description = "获取按维度类目分组的维度列表，支持关键词搜索、类目过滤和每类目数量限制")
    public R<List<DimensionCategoryGroupDTO>> listDimensionsGroupByCategory(
            @Parameter(description = "数据源 ID") @PathVariable Long datasourceId,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "类目 ID 过滤") @RequestParam(required = false) String categoryId,
            @Parameter(description = "每类目最大返回条数，小于等于 0 表示不限制") @RequestParam(required = false, defaultValue = "0") int limitPerCategory) {
        return R.ok(aloudataSyncService.listDimensionsGroupByCategory(datasourceId, keyword, categoryId, limitPerCategory));
    }

    /**
     * 查询类目下的指标/维度数量统计
     */
    @GetMapping("/{datasourceId}/aloudata/categories/counts")
    @Operation(summary = "类目数量统计", description = "统计各类目下的指标或维度数量，用于前端类目树展示")
    public R<List<AloudataCategoryCountDTO>> listCategoryCounts(
            @Parameter(description = "数据源 ID") @PathVariable Long datasourceId,
            @Parameter(description = "类目类型：CATEGORY_METRIC / CATEGORY_DIMENSION") @RequestParam String categoryType) {
        return R.ok(aloudataSyncService.listCategoryCounts(datasourceId, categoryType));
    }

    /**
     * 获取 Aloudata API 端点参数规范
     */
    @GetMapping("/aloudata/api-specs")
    @Operation(summary = "获取 Aloudata API 端点参数规范",
            description = "获取所有 Aloudata API 端点的请求参数和响应参数规范定义，"
                    + "包括参数名称、类型、是否必填、默认值、传递方式和说明")
    public R<Map<String, ApiEndpoint>> getAloudataApiSpecs() {
        return R.ok(aloudataEndpointService.getEndpoints());
    }
}