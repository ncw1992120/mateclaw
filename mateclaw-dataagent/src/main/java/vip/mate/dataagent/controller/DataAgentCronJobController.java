package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vip.mate.common.result.R;
import vip.mate.cron.model.CronJobDTO;
import vip.mate.dataagent.auth.annotation.RequireWorkspaceRole;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.sdk.service.MateClawRuntime;

import java.util.List;

/**
 * 定时任务管理控制器
 * <p>
 * 提供定时任务 CRUD、启停、立即执行等 API，通过 SDK 委托 mateclaw-server 执行业务逻辑。
 */
@RestController
@RequestMapping("/v1/cron-jobs")
@RequiredArgsConstructor
@Tag(name = "定时任务管理", description = "定时任务 CRUD、启停、立即执行接口")
public class DataAgentCronJobController {

    private final MateClawRuntime runtime;
    private final WorkspaceGuard workspaceGuard;

    /**
     * 获取定时任务列表
     */
    @GetMapping
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "定时任务列表", description = "按工作区列出定时任务")
    public R<List<CronJobDTO>> list() {
        return R.ok(runtime.listCronJobs(workspaceGuard.currentWorkspaceId()));
    }

    /**
     * 获取定时任务详情
     */
    @GetMapping("/{id}")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "定时任务详情", description = "根据 ID 获取定时任务详情")
    public R<CronJobDTO> get(@PathVariable Long id) {
        return R.ok(runtime.getCronJob(id, workspaceGuard.currentWorkspaceId()));
    }

    /**
     * 创建定时任务
     */
    @PostMapping
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    @Operation(summary = "创建定时任务", description = "新增定时任务配置（仅管理员）")
    public R<CronJobDTO> create(@RequestBody CronJobDTO dto) {
        return R.ok(runtime.createCronJob(dto, workspaceGuard.currentWorkspaceId()));
    }

    /**
     * 更新定时任务
     */
    @PutMapping("/{id}")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    @Operation(summary = "更新定时任务", description = "更新定时任务配置（仅管理员）")
    public R<CronJobDTO> update(@PathVariable Long id, @RequestBody CronJobDTO dto) {
        return R.ok(runtime.updateCronJob(id, dto, workspaceGuard.currentWorkspaceId()));
    }

    /**
     * 删除定时任务
     */
    @DeleteMapping("/{id}")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    @Operation(summary = "删除定时任务", description = "删除指定定时任务（仅管理员）")
    public R<Void> delete(@PathVariable Long id) {
        runtime.deleteCronJob(id, workspaceGuard.currentWorkspaceId());
        return R.ok();
    }

    /**
     * 启用/禁用定时任务
     */
    @PutMapping("/{id}/toggle")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    @Operation(summary = "启用/禁用定时任务", description = "切换定时任务启停状态（仅管理员）")
    public R<Void> toggle(@PathVariable Long id, @RequestParam boolean enabled) {
        runtime.toggleCronJob(id, enabled, workspaceGuard.currentWorkspaceId());
        return R.ok();
    }

    /**
     * 立即执行定时任务
     */
    @PostMapping("/{id}/run")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    @Operation(summary = "立即执行定时任务", description = "手动触发一次定时任务执行（仅管理员）")
    public R<Void> runNow(@PathVariable Long id) {
        runtime.runCronJobNow(id, workspaceGuard.currentWorkspaceId());
        return R.ok();
    }
}
