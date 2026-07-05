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
import vip.mate.dataagent.dto.LogicalRelationCreateRequest;
import vip.mate.dataagent.dto.LogicalRelationUpdateRequest;
import vip.mate.dataagent.dto.LogicalRelationVO;
import vip.mate.dataagent.service.LogicalRelationService;

import java.util.List;

/**
 * 逻辑外键关系管理控制器
 * <p>
 * 提供逻辑外键关系的 CRUD、按表查询、自动初始化等 API。
 */
@RestController
@RequestMapping("/v1/logical-relations")
@RequiredArgsConstructor
@Tag(name = "逻辑外键关系管理", description = "逻辑外键关系 CRUD 与自动初始化接口")
public class DataAgentLogicalRelationController {

    private final LogicalRelationService logicalRelationService;

    /**
     * 按数据源查询所有逻辑外键关系
     */
    @GetMapping
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "查询逻辑外键关系列表", description = "按数据源 ID 查询所有逻辑外键关系")
    public R<List<LogicalRelationVO>> list(
            @Parameter(description = "数据源 ID") @RequestParam Long datasourceId,
            @Parameter(description = "表名（可选，多个用逗号分隔）") @RequestParam(required = false) String tableNames) {
        if (tableNames != null && !tableNames.isBlank()) {
            List<String> names = List.of(tableNames.split(","));
            return R.ok(logicalRelationService.listByDatasourceIdAndTableNames(datasourceId, names));
        }
        return R.ok(logicalRelationService.listByDatasourceId(datasourceId));
    }

    /**
     * 获取逻辑外键关系详情
     */
    @GetMapping("/{id}")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "获取逻辑外键关系详情", description = "根据 ID 获取逻辑外键关系详情")
    public R<LogicalRelationVO> get(
            @Parameter(description = "逻辑外键关系 ID") @PathVariable Long id) {
        return R.ok(logicalRelationService.getById(id));
    }

    /**
     * 创建逻辑外键关系
     */
    @PostMapping
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    @Operation(summary = "创建逻辑外键关系", description = "定义表间的逻辑关联关系")
    public R<LogicalRelationVO> create(@RequestBody LogicalRelationCreateRequest request) {
        return R.ok(logicalRelationService.create(request));
    }

    /**
     * 更新逻辑外键关系
     */
    @PutMapping("/{id}")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    @Operation(summary = "更新逻辑外键关系", description = "更新逻辑外键关系的关系类型和描述")
    public R<LogicalRelationVO> update(
            @Parameter(description = "逻辑外键关系 ID") @PathVariable Long id,
            @RequestBody LogicalRelationUpdateRequest request) {
        return R.ok(logicalRelationService.update(id, request));
    }

    /**
     * 删除逻辑外键关系
     */
    @DeleteMapping("/{id}")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    @Operation(summary = "删除逻辑外键关系", description = "删除指定逻辑外键关系")
    public R<Void> delete(
            @Parameter(description = "逻辑外键关系 ID") @PathVariable Long id) {
        logicalRelationService.delete(id);
        return R.ok(null);
    }

    /**
     * 从物理外键自动初始化逻辑外键关系
     */
    @PostMapping("/auto-init")
    @RequireGlobalAdmin
    @Operation(summary = "自动初始化", description = "从物理外键自动生成逻辑外键关系记录，仅创建不存在的记录。仅全局管理员可执行同步元数据操作")
    public R<Integer> autoInit(
            @Parameter(description = "数据源 ID") @RequestParam Long datasourceId) {
        return R.ok(logicalRelationService.autoInitFromPhysicalForeignKeys(datasourceId));
    }
}
