package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vip.mate.common.result.R;
import vip.mate.dataagent.auth.annotation.RequireWorkspaceRole;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.ResourceGrantRequest;
import vip.mate.dataagent.dto.ResourceGrantUpdateRequest;
import vip.mate.dataagent.model.ResourceGrantEntity;
import vip.mate.dataagent.service.ResourceGrantService;

import java.util.List;

/**
 * 通用资源授权管理控制器
 * <p>
 * 提供资源授权的 CRUD 与权限查询接口，支持 skill 授权、发布审批等场景。
 */
@RestController
@RequestMapping("/v1/resource-grants")
@RequiredArgsConstructor
@Tag(name = "资源授权管理", description = "通用资源授权 CRUD 与权限查询接口")
public class ResourceGrantController {

    private final ResourceGrantService resourceGrantService;
    private final WorkspaceGuard workspaceGuard;

    /**
     * 列出指定资源的授权记录
     */
    @GetMapping
    @Operation(summary = "资源授权列表", description = "按资源类型和 ID 查询授权记录")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    public R<List<ResourceGrantEntity>> listByResource(
            @Parameter(description = "资源类型") @RequestParam String resourceType,
            @Parameter(description = "资源 ID") @RequestParam Long resourceId) {
        return R.ok(resourceGrantService.listGrantsByResource(
                workspaceGuard.currentWorkspaceId(), resourceType, resourceId));
    }

    /**
     * 列出被授权者的授权记录
     */
    @GetMapping("/grantee")
    @Operation(summary = "被授权者授权列表", description = "按被授权者标识查询授权记录")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    public R<List<ResourceGrantEntity>> listByGrantee(
            @Parameter(description = "授权类型") @RequestParam String grantType,
            @Parameter(description = "被授权者标识") @RequestParam String granteeId,
            @Parameter(description = "状态过滤") @RequestParam(required = false) Integer status) {
        return R.ok(resourceGrantService.listGrantsByGrantee(
                workspaceGuard.currentWorkspaceId(), grantType, granteeId, status));
    }

    /**
     * 列出当前工作区的所有授权记录
     */
    @GetMapping("/workspace")
    @Operation(summary = "工作区授权清单", description = "查询当前工作区下所有授权记录，支持按资源类型和状态过滤")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    public R<List<ResourceGrantEntity>> listByWorkspace(
            @Parameter(description = "资源类型过滤") @RequestParam(required = false) String resourceType,
            @Parameter(description = "状态过滤") @RequestParam(required = false) Integer status) {
        return R.ok(resourceGrantService.listGrantsByWorkspace(
                workspaceGuard.currentWorkspaceId(), resourceType, status));
    }

    /**
     * 授予权限
     */
    @PostMapping
    @Operation(summary = "授予权限", description = "为指定资源创建授权记录")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    public R<ResourceGrantEntity> grant(@RequestBody ResourceGrantRequest request) {
        return R.ok(resourceGrantService.grant(request));
    }

    /**
     * 更新授权记录（权限、过期时间）
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新授权", description = "修改授权记录的权限和过期时间")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    public R<ResourceGrantEntity> update(
            @Parameter(description = "授权记录 ID") @PathVariable Long id,
            @RequestBody ResourceGrantUpdateRequest request) {
        return R.ok(resourceGrantService.updateGrant(id, request.getPermission(), request.getExpireTime()));
    }

    /**
     * 撤销授权
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "撤销授权", description = "撤销指定的授权记录")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    public R<Void> revoke(@Parameter(description = "授权记录 ID") @PathVariable Long id) {
        resourceGrantService.revoke(id);
        return R.ok(null);
    }

    /**
     * 检查权限
     */
    @GetMapping("/check")
    @Operation(summary = "权限检查", description = "检查被授权者对指定资源是否具有指定权限")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    public R<Boolean> checkPermission(
            @Parameter(description = "资源类型") @RequestParam String resourceType,
            @Parameter(description = "资源 ID") @RequestParam Long resourceId,
            @Parameter(description = "授权类型") @RequestParam String grantType,
            @Parameter(description = "被授权者标识") @RequestParam String granteeId,
            @Parameter(description = "权限") @RequestParam String permission) {
        return R.ok(resourceGrantService.checkPermission(
                workspaceGuard.currentWorkspaceId(), resourceType, resourceId,
                grantType, granteeId, permission));
    }
}
