package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vip.mate.common.result.R;
import vip.mate.dataagent.dto.SkillInstallRequest;
import vip.mate.sdk.service.MateClawRuntime;
import vip.mate.skill.installer.model.HubSkillInfo;
import vip.mate.skill.installer.model.InstallRequest;
import vip.mate.skill.installer.model.InstallTask;

import java.util.List;
import java.util.Map;

/**
 * 技能导入控制器
 * <p>
 * 代理 mateclaw-server 的技能安装接口，对外提供三种导入方式：
 * <ul>
 *   <li>从 URL 安装（GitHub 仓库或 ClawHub 市场）</li>
 *   <li>从 ClawHub 市场搜索与安装</li>
 *   <li>从本地 ZIP 包安装</li>
 * </ul>
 * 同时提供安装任务状态查询、取消与按名称卸载等能力。
 */
@Slf4j
@RestController
@RequestMapping("/v1/skills/install")
@RequiredArgsConstructor
@Tag(name = "技能导入", description = "智能问数工作台技能导入接口（支持 URL、市场、ZIP 三种方式）")
public class DataAgentSkillInstallController {

    private final MateClawRuntime runtime;

    /**
     * 搜索 ClawHub 市场
     */
    @GetMapping("/hub/search")
    @Operation(summary = "搜索技能市场", description = "在 ClawHub 市场中按关键字搜索可用技能")
    public R<List<HubSkillInfo>> searchHub(
            @RequestParam(name = "q", required = false, defaultValue = "") String query,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return R.ok(runtime.searchSkillHub(query, limit));
    }

    /**
     * 启动异步安装任务（从 URL / 市场）
     */
    @PostMapping("/start")
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
        serverRequest.setWorkspaceId(request.getWorkspaceId());
        return R.ok(runtime.startInstallSkill(serverRequest));
    }

    /**
     * 查询安装任务状态
     */
    @GetMapping("/status/{taskId}")
    @Operation(summary = "查询安装任务", description = "根据 taskId 查询安装任务状态")
    public R<InstallTask> getStatus(@PathVariable String taskId) {
        InstallTask task = runtime.getInstallTaskStatus(taskId);
        if (task == null) {
            return R.fail("任务不存在: " + taskId);
        }
        return R.ok(task);
    }

    /**
     * 取消安装任务
     */
    @PostMapping("/cancel/{taskId}")
    @Operation(summary = "取消安装任务", description = "取消正在执行的安装任务")
    public R<Void> cancel(@PathVariable String taskId) {
        runtime.cancelInstallTask(taskId);
        return R.ok();
    }

    /**
     * 上传 ZIP 包安装
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传 ZIP 安装", description = "上传 .zip 包同步安装技能")
    public R<Map<String, Object>> uploadZip(
            @RequestPart("file") MultipartFile zipFile,
            @RequestParam(name = "enable", defaultValue = "true") Boolean enable,
            @RequestParam(name = "overwrite", defaultValue = "false") Boolean overwrite,
            @RequestParam(name = "targetName", required = false) String targetName,
            @RequestParam(name = "workspaceId", required = false) Long workspaceId) {
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
                    workspaceId);
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
    @DeleteMapping("/{skillName}")
    @Operation(summary = "卸载技能", description = "根据 skill 名称卸载（逻辑删除 + 工作区归档）")
    public R<Map<String, String>> uninstall(@PathVariable String skillName,
                                            @RequestParam(name = "workspaceId", required = false) Long workspaceId) {
        runtime.uninstallSkillByName(skillName, workspaceId);
        return R.ok(Map.of("message", "技能 " + skillName + " 已卸载"));
    }
}
