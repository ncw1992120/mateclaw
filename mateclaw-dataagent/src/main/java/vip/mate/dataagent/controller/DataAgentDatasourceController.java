package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vip.mate.common.result.R;
import vip.mate.dataagent.aloudata.AloudataApiProperties.ApiEndpoint;
import vip.mate.dataagent.aloudata.AloudataEndpointService;
import vip.mate.dataagent.dto.*;
import vip.mate.dataagent.service.AloudataService;
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
    private final AloudataService aloudataService;
    private final AloudataEndpointService aloudataEndpointService;

    /**
     * 数据源列表
     */
    @GetMapping
    @Operation(summary = "数据源列表", description = "获取所有数据源")
    public R<List<DatasourceVO>> list() {
        return R.ok(datasourceService.listDatasources());
    }

    /**
     * 数据源详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "数据源详情", description = "根据 ID 获取数据源详情")
    public R<DatasourceVO> get(
            @Parameter(description = "数据源 ID") @PathVariable Long id) {
        return R.ok(datasourceService.getDatasource(id));
    }

    /**
     * 创建数据源
     */
    @PostMapping
    @Operation(summary = "创建数据源", description = "新增数据源配置")
    public R<DatasourceVO> create(@RequestBody DatasourceCreateRequest request) {
        return R.ok(datasourceService.createDatasource(request));
    }

    /**
     * 更新数据源
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新数据源", description = "更新数据源配置")
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
     * 触发 Schema 发现
     */
    @PostMapping("/{id}/schema-discovery")
    @Operation(summary = "触发 Schema 发现", description = "自动扫描数据源，提取表结构、字段类型、主外键关系、索引信息")
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
     * 同步单张表元数据
     */
    @PostMapping("/{datasourceId}/tables/{tableId}/sync")
    @Operation(summary = "同步单张表元数据", description = "重新同步指定表的字段信息，支持追加和覆盖模式")
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
     * 查询 Aloudata 指标列表
     */
    @GetMapping("/{datasourceId}/aloudata/metrics")
    @Operation(summary = "查询 Aloudata 指标列表", description = "获取 Aloudata 指标平台下的所有指标列表")
    public R<List<AloudataMetricVO>> listAloudataMetrics(
            @Parameter(description = "数据源 ID") @PathVariable Long datasourceId) {
        return R.ok(aloudataService.listMetrics(datasourceId));
    }

    /**
     * 执行 Aloudata 指标数据查询
     */
    @PostMapping("/{datasourceId}/aloudata/query")
    @Operation(summary = "执行 Aloudata 指标查询", description = "使用指标和维度组合，查询指定的指标计算结果")
    public R<AloudataMetricQueryResponse> queryAloudataMetrics(
            @Parameter(description = "数据源 ID") @PathVariable Long datasourceId,
            @RequestBody AloudataMetricQueryRequest request) {
        return R.ok(aloudataService.queryMetrics(datasourceId, request));
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