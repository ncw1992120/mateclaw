package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;
import vip.mate.common.result.R;
import vip.mate.dataagent.auth.annotation.RequireWorkspaceRole;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.sdk.service.WorkspaceFileRuntime;
import vip.mate.workspace.document.model.WorkspaceFileEntity;

import java.util.List;

/**
 * 智能体上下文管理控制器
 * <p>
 * 管理 Agent 级别的 Markdown 文档（如 PROFILE.md、MEMORY.md、SOUL.md、AGENTS.md），
 * 支持文件 CRUD、系统提示文件启用/排序，为前端"工作空间-智能体上下文"页面提供接口。
 */
@RestController
@RequestMapping("/v1/agents/{agentId}/context")
@RequiredArgsConstructor
@Tag(name = "智能体上下文管理", description = "管理 Agent 的工作区文件（PROFILE.md / MEMORY.md / SOUL.md 等）")
public class DataAgentAgentContextController {

    private final WorkspaceFileRuntime workspaceFileRuntime;

    /**
     * 列出 Agent 的所有工作区文件（不含内容）
     */
    @GetMapping("/files")
    @Operation(summary = "列出工作区文件", description = "列出 Agent 的所有共享工作区文件（不含内容）")
    public R<List<WorkspaceFileEntity>> listFiles(@PathVariable Long agentId) {
        return R.ok(workspaceFileRuntime.listFiles(agentId));
    }

    /**
     * 读取单个文件内容（支持子目录，如 memory/2026-04-03.md）
     */
    @GetMapping("/files/**")
    @Operation(summary = "读取工作区文件", description = "按文件名读取单个工作区文件内容")
    public R<WorkspaceFileEntity> getFile(@PathVariable Long agentId, HttpServletRequest request) {
        String filename = extractFilename(request);
        WorkspaceFileEntity file = workspaceFileRuntime.getFile(agentId, filename);
        if (file == null) {
            return R.fail("文件不存在: " + filename);
        }
        return R.ok(file);
    }

    /**
     * 创建或更新文件（支持子目录）
     */
    @PutMapping("/files/**")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    @Operation(summary = "保存工作区文件", description = "创建或更新工作区文件内容（仅管理员）")
    public R<WorkspaceFileEntity> saveFile(@PathVariable Long agentId,
                                            HttpServletRequest httpRequest,
                                            @RequestBody SaveFileRequest body) {
        String filename = extractFilename(httpRequest);
        return R.ok(workspaceFileRuntime.saveFile(agentId, filename, body.getContent()));
    }

    /**
     * 删除文件（支持子目录）
     */
    @DeleteMapping("/files/**")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    @Operation(summary = "删除工作区文件", description = "删除指定的工作区文件（仅管理员）")
    public R<Void> deleteFile(@PathVariable Long agentId, HttpServletRequest request) {
        String filename = extractFilename(request);
        workspaceFileRuntime.deleteFile(agentId, filename);
        return R.ok();
    }

    /**
     * 获取启用的系统提示文件名列表（有序）
     */
    @GetMapping("/prompt-files")
    @Operation(summary = "获取系统提示文件列表", description = "获取当前启用为系统提示的文件名列表（有序）")
    public R<List<String>> getPromptFiles(@PathVariable Long agentId) {
        return R.ok(workspaceFileRuntime.getPromptFiles(agentId));
    }

    /**
     * 设置启用的系统提示文件列表（有序）
     */
    @PutMapping("/prompt-files")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)
    @Operation(summary = "设置系统提示文件列表", description = "批量设置启用为系统提示的文件列表（有序，仅管理员）")
    public R<Void> setPromptFiles(@PathVariable Long agentId,
                                   @RequestBody PromptFilesRequest request) {
        workspaceFileRuntime.setPromptFiles(agentId, request.getFiles());
        return R.ok();
    }

    /**
     * 从请求路径中提取 /files/ 之后的文件名部分（支持含 / 的子目录路径）
     */
    private String extractFilename(HttpServletRequest request) {
        String fullPath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        int filesIdx = fullPath.indexOf("/context/files/");
        return fullPath.substring(filesIdx + "/context/files/".length());
    }

    /**
     * 保存文件请求体
     */
    @Data
    static class SaveFileRequest {
        /** Markdown 内容 */
        private String content;
    }

    /**
     * 设置系统提示文件列表请求体
     */
    @Data
    static class PromptFilesRequest {
        /** 文件名列表 */
        private List<String> files;
    }
}
