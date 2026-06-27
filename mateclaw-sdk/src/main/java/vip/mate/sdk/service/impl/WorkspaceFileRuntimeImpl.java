package vip.mate.sdk.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vip.mate.sdk.service.WorkspaceFileRuntime;
import vip.mate.workspace.document.WorkspaceFileService;
import vip.mate.workspace.document.model.WorkspaceFileEntity;

import java.util.List;

/**
 * 工作区文件运行时实现
 * <p>
 * 委托给 {@link WorkspaceFileService}，为宿主应用提供
 * Agent 级别 Markdown 文档管理的编程式访问能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceFileRuntimeImpl implements WorkspaceFileRuntime {

    private final WorkspaceFileService workspaceFileService;

    @Override
    public List<WorkspaceFileEntity> listFiles(Long agentId) {
        return workspaceFileService.listVisibleFiles(agentId, null);
    }

    @Override
    public WorkspaceFileEntity getFile(Long agentId, String filename) {
        return workspaceFileService.getFile(agentId, filename);
    }

    @Override
    public WorkspaceFileEntity saveFile(Long agentId, String filename, String content) {
        return workspaceFileService.saveFile(agentId, filename, content);
    }

    @Override
    public void deleteFile(Long agentId, String filename) {
        workspaceFileService.deleteFile(agentId, filename);
    }

    @Override
    public List<String> getPromptFiles(Long agentId) {
        return workspaceFileService.getPromptFiles(agentId);
    }

    @Override
    public void setPromptFiles(Long agentId, List<String> filenames) {
        workspaceFileService.setPromptFiles(agentId, filenames);
    }
}
