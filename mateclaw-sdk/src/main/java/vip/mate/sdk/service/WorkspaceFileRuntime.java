package vip.mate.sdk.service;

import vip.mate.workspace.document.model.WorkspaceFileEntity;

import java.util.List;

/**
 * 工作区文件运行时接口
 * <p>
 * 封装 Agent 级别的 Markdown 文档管理能力，包括文件 CRUD、
 * 系统提示文件启用/排序等。宿主应用通过此接口管理智能体上下文文件
 * （如 PROFILE.md、MEMORY.md、SOUL.md、AGENTS.md 等）。
 */
public interface WorkspaceFileRuntime {

    /**
     * 列出 Agent 的所有共享工作区文件（不含内容）
     *
     * @param agentId Agent ID
     * @return 工作区文件列表（content 字段为 null）
     */
    List<WorkspaceFileEntity> listFiles(Long agentId);

    /**
     * 读取单个共享文件内容
     *
     * @param agentId  Agent ID
     * @param filename 文件名（支持子目录如 memory/2026-04-03.md）
     * @return 工作区文件实体，不存在时返回 null
     */
    WorkspaceFileEntity getFile(Long agentId, String filename);

    /**
     * 创建或更新共享文件
     *
     * @param agentId  Agent ID
     * @param filename 文件名
     * @param content  Markdown 内容
     * @return 保存后的文件实体
     */
    WorkspaceFileEntity saveFile(Long agentId, String filename, String content);

    /**
     * 删除共享文件
     *
     * @param agentId  Agent ID
     * @param filename 文件名
     */
    void deleteFile(Long agentId, String filename);

    /**
     * 获取当前启用的系统提示文件名列表（有序）
     *
     * @param agentId Agent ID
     * @return 启用的文件名列表
     */
    List<String> getPromptFiles(Long agentId);

    /**
     * 设置启用的系统提示文件列表（有序）
     *
     * @param agentId  Agent ID
     * @param filenames 文件名列表（按顺序启用）
     */
    void setPromptFiles(Long agentId, List<String> filenames);
}
