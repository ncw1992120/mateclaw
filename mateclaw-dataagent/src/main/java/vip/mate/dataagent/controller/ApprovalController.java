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
import vip.mate.dataagent.dto.ApprovalProcessRequest;
import vip.mate.dataagent.dto.ApprovalSubmitRequest;
import vip.mate.dataagent.model.ApprovalRecordEntity;
import vip.mate.dataagent.service.ApprovalService;

import java.util.List;

/**
 * 审批流程管理控制器
 * <p>
 * 提供审批申请提交、审批通过/拒绝/撤回、审批记录查询接口。
 */
@RestController
@RequestMapping("/v1/approvals")
@RequiredArgsConstructor
@Tag(name = "审批流程管理", description = "审批申请提交、审批处理与查询接口")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final WorkspaceGuard workspaceGuard;

    /**
     * 提交审批申请
     */
    @PostMapping
    @Operation(summary = "提交审批申请", description = "提交资源发布/授权等操作的审批申请")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    public R<ApprovalRecordEntity> submit(@RequestBody ApprovalSubmitRequest request) {
        return R.ok(approvalService.submit(request));
    }

    /**
     * 通过审批
     */
    @PutMapping("/{id}/approve")
    @Operation(summary = "通过审批", description = "审批人通过指定审批申请")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    public R<Void> approve(
            @Parameter(description = "审批记录 ID") @PathVariable Long id,
            @RequestBody(required = false) ApprovalProcessRequest request) {
        approvalService.approve(id, request);
        return R.ok(null);
    }

    /**
     * 拒绝审批
     */
    @PutMapping("/{id}/reject")
    @Operation(summary = "拒绝审批", description = "审批人拒绝指定审批申请")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    public R<Void> reject(
            @Parameter(description = "审批记录 ID") @PathVariable Long id,
            @RequestBody(required = false) ApprovalProcessRequest request) {
        approvalService.reject(id, request);
        return R.ok(null);
    }

    /**
     * 撤回审批
     */
    @PutMapping("/{id}/cancel")
    @Operation(summary = "撤回审批", description = "申请人撤回自己的审批申请")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    public R<Void> cancel(@Parameter(description = "审批记录 ID") @PathVariable Long id) {
        approvalService.cancel(id);
        return R.ok(null);
    }

    /**
     * 审批记录列表
     */
    @GetMapping
    @Operation(summary = "审批记录列表", description = "按状态和资源类型过滤审批记录")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    public R<List<ApprovalRecordEntity>> list(
            @Parameter(description = "状态过滤") @RequestParam(required = false) String status,
            @Parameter(description = "资源类型过滤") @RequestParam(required = false) String resourceType) {
        return R.ok(approvalService.listApprovals(
                workspaceGuard.currentWorkspaceId(), status, resourceType));
    }

    /**
     * 审批详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "审批详情", description = "获取指定审批记录详情")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    public R<ApprovalRecordEntity> get(@Parameter(description = "审批记录 ID") @PathVariable Long id) {
        return R.ok(approvalService.getApproval(id));
    }
}
