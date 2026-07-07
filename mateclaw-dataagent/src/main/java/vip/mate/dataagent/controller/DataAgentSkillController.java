package vip.mate.dataagent.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vip.mate.common.result.R;
import vip.mate.dataagent.auth.annotation.RequireWorkspaceRole;
import vip.mate.dataagent.auth.service.SkillGuard;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.SkillInstallRequest;
import vip.mate.sdk.service.MateClawRuntime;
import vip.mate.skill.installer.model.HubSkillInfo;
import vip.mate.skill.installer.model.InstallRequest;
import vip.mate.skill.installer.model.InstallTask;
import vip.mate.skill.model.SkillEntity;

import java.util.List;
import java.util.Map;

/**
 * 技能管理控制器
 * <p>
 * 代理 mateclaw-server 的技能管理接口，供"智能问数"工作台使用。
 * 提供技能的分页查询、详情、创建、更新、删除、启停切换等能力。
 * 同时提供技能导入能力，支持 URL、市场、ZIP 三种安装方式。
 */
@Slf4j
@RestController
@RequestMapping("/v1/skills")
@RequiredArgsConstructor
@Tag(name = "技能管理", description = "智能问数工作台技能管理接口（代理 mateclaw-server）")
public class DataAgentSkillController {

    private final MateClawRuntime runtime;
    private final WorkspaceGuard workspaceGuard;
    private final SkillGuard skillGuard;

    /**
     * 技能分页列表
     */
    @GetMapping
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "技能分页列表", description = "按工作区查询技能分页数据，支持关键字、类型、启用状态过滤")
    public R<IPage<SkillEntity>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String skillType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String lifecycleState) {
        return R.ok(runtime.pageSkills(page, size, keyword, skillType, enabled,
                workspaceGuard.currentWorkspaceId(), sort, lifecycleState));
    }

    /**
     * 技能列表（不分页）
     */
    @GetMapping("/all")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "技能列表", description = "获取工作区下所有技能，不分页")
    public R<List<SkillEntity>> list() {
        return R.ok(runtime.listSkills(workspaceGuard.currentWorkspaceId()));
    }

    /**
     * 已启用技能列表
     */
    @GetMapping("/enabled")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "已启用技能", description = "获取工作区下已启用的技能列表")
    public R<List<SkillEntity>> listEnabled() {
        return R.ok(runtime.listEnabledSkills(workspaceGuard.currentWorkspaceId()));
    }

    /**
     * 技能详情
     */
    @GetMapping("/{id}")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "技能详情", description = "根据 ID 获取技能详情")
    public R<SkillEntity> get(@PathVariable Long id) {
        skillGuard.requireSkillInCurrentWorkspace(id);
        return R.ok(runtime.getSkill(id));
    }

    /**
     * 创建技能
     */
    @PostMapping
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    @Operation(summary = "创建技能", description = "新增技能配置")
    public R<SkillEntity> create(@RequestBody SkillEntity entity) {
        entity.setWorkspaceId(workspaceGuard.currentWorkspaceId());
        return R.ok(runtime.createSkill(entity));
    }

    /**
     * 更新技能
     */
    @PutMapping("/{id}")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    @Operation(summary = "更新技能", description = "更新技能配置")
    public R<SkillEntity> update(@PathVariable Long id, @RequestBody SkillEntity entity) {
        skillGuard.requireSkillInCurrentWorkspace(id);
        entity.setId(id);
        return R.ok(runtime.updateSkill(entity));
    }

    /**
     * 硬删除技能
     */
    @DeleteMapping("/{id}")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    @Operation(summary = "删除技能", description = "硬删除指定技能（不可恢复）")
    public R<Void> delete(@PathVariable Long id) {
        skillGuard.requireSkillInCurrentWorkspace(id);
        runtime.hardDeleteSkill(id);
        return R.ok();
    }

    /**
     * 切换技能启停状态
     */
    @PutMapping("/{id}/toggle")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    @Operation(summary = "启停切换", description = "启用或禁用指定技能")
    public R<SkillEntity> toggle(@PathVariable Long id, @RequestParam boolean enabled) {
        skillGuard.requireSkillInCurrentWorkspace(id);
        return R.ok(runtime.toggleSkill(id, enabled));
    }

    // ==================== 技能导入 ====================

    /**
     * 搜索 ClawHub 市场
     */
    @GetMapping("/install/hub/search")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "搜索技能市场", description = "在 ClawHub 市场中按关键字搜索可用技能")
    public R<List<HubSkillInfo>> searchHub(
            @RequestParam(name = "q", required = false, defaultValue = "") String query,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return R.ok(runtime.searchSkillHub(query, limit));
    }

    /**
     * 启动异步安装任务（从 URL / 市场）
     */
    @PostMapping("/install/start")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    @Operation(summary = "启动技能安装", description = "根据 bundleUrl 启动一个异步安装任务，支持 GitHub 仓库或 ClawHub 市场")
    public R<InstallTask> startInstall(@RequestBody SkillInstallRequest request) {
        if (request == null || request.getBundleUrl() == null || request.getBundleUrl().isBlank()) {
            return R.fail("bundleUrl 不能为空");
        }
        InstallRequest serverRequest = new InstallRequest();
        serverRequest.setBundleUrl(request.getBundleUrl());
        serverRequest.setVersion(request.getVersion());
        serverRequest.setEnable(request.getEnable() == null ? Boolean.TRUE : request.getEnable());
        serverRequest.setTargetName(request.getTargetName());
        serverRequest.setOverwrite(request.getOverwrite() == null ? Boolean.FALSE : request.getOverwrite());
        serverRequest.setWorkspaceId(workspaceGuard.currentWorkspaceId());
        return R.ok(runtime.startInstallSkill(serverRequest));
    }

    /**
     * 查询安装任务状态
     */
    @GetMapping("/install/status/{taskId}")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "查询安装任务", description = "根据 taskId 查询安装任务状态")
    public R<InstallTask> getInstallStatus(@PathVariable String taskId) {
        InstallTask task = runtime.getInstallTaskStatus(taskId);
        if (task == null) {
            return R.fail("任务不存在: " + taskId);
        }
        return R.ok(task);
    }

    /**
     * 取消安装任务
     */
    @PostMapping("/install/cancel/{taskId}")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    @Operation(summary = "取消安装任务", description = "取消正在执行的安装任务")
    public R<Void> cancelInstall(@PathVariable String taskId) {
        runtime.cancelInstallTask(taskId);
        return R.ok();
    }

    /**
     * 上传 ZIP 包安装
     */
    @PostMapping(value = "/install/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    @Operation(summary = "上传 ZIP 安装", description = "上传 .zip 包同步安装技能")
    public R<Map<String, Object>> uploadZip(
            @RequestPart("file") MultipartFile zipFile,
            @RequestParam(name = "enable", defaultValue = "true") Boolean enable,
            @RequestParam(name = "overwrite", defaultValue = "false") Boolean overwrite,
            @RequestParam(name = "targetName", required = false) String targetName) {
        if (zipFile == null || zipFile.isEmpty()) {
            return R.fail("ZIP 文件不能为空");
        }
        String filename = zipFile.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
            return R.fail("仅支持 .zip 文件");
        }
        try {
            Map<String, Object> result = runtime.installSkillFromZip(
                    zipFile,
                    enable != null && enable,
                    overwrite != null && overwrite,
                    targetName,
                    workspaceGuard.currentWorkspaceId());
            return R.ok(result);
        } catch (IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        } catch (Exception e) {
            log.error("ZIP install failed: {}", e.getMessage(), e);
            return R.fail("ZIP 安装失败: " + e.getMessage());
        }
    }

    /**
     * 按名称卸载技能
     */
    @DeleteMapping("/install/{skillName}")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    @Operation(summary = "卸载技能", description = "根据 skill 名称卸载（逻辑删除 + 工作区归档）")
    public R<Map<String, String>> uninstallByName(@PathVariable String skillName) {
        runtime.uninstallSkillByName(skillName, workspaceGuard.currentWorkspaceId());
        return R.ok(Map.of("message", "技能 " + skillName + " 已卸载"));
    }
}
